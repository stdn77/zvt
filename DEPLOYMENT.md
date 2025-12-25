# ZVIT Backend - Deployment Guide

## Зміст
1. [Dockerизація](#1-dockerизація)
2. [Підготовка AWS](#2-підготовка-aws)
3. [Розгортання на AWS](#3-розгортання-на-aws)
4. [Налаштування домену та SSL](#4-налаштування-домену-та-ssl)
5. [Моніторинг та обслуговування](#5-моніторинг-та-обслуговування)

---

## 1. Dockerізація

### 1.1 Створення Dockerfile

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Копіюємо gradle файли для кешування залежностей
COPY gradle gradle
COPY gradlew .
COPY build.gradle .
COPY settings.gradle .

# Завантажуємо залежності (кешується якщо build.gradle не змінився)
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon

# Копіюємо код і будуємо
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Фінальний образ
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Створюємо користувача для безпеки
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Копіюємо JAR
COPY --from=builder /app/build/libs/*.jar app.jar

# Створюємо директорію для логів
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

# Порт
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Запуск з оптимізаціями для контейнера
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
```

### 1.2 Docker Compose для локального тестування

```yaml
# docker-compose.yml
version: '3.8'

services:
  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USERNAME=${DATABASE_USERNAME}
      - DATABASE_PASSWORD=${DATABASE_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - FIREBASE_CREDENTIALS=${FIREBASE_CREDENTIALS}
      - ENCRYPTION_KEY=${ENCRYPTION_KEY}
    volumes:
      - ./logs:/app/logs
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # PostgreSQL для локального тестування
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: zvit
      POSTGRES_USER: zvit
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

volumes:
  postgres_data:
```

### 1.3 Файл .dockerignore

```
# .dockerignore
.git
.gitignore
.gradle
build
*.md
*.log
.idea
*.iml
docker-compose*.yml
Dockerfile*
.env*
```

### 1.4 Локальне тестування

```bash
# Збірка образу
docker build -t zvit-backend:latest .

# Запуск з docker-compose
docker-compose up -d

# Перевірка логів
docker-compose logs -f backend

# Зупинка
docker-compose down
```

---

## 2. Підготовка AWS

### 2.1 Реєстрація AWS акаунту

1. Перейти на https://aws.amazon.com/
2. Натиснути "Create an AWS Account"
3. Заповнити:
   - Email address
   - Password
   - AWS account name (наприклад: "zvit-production")
4. Вибрати "Personal" або "Business" account
5. Ввести платіжну інформацію (карта потрібна для верифікації)
6. Підтвердити телефон
7. Вибрати Support Plan (Basic - безкоштовний)

### 2.2 Налаштування безпеки (IAM)

```bash
# 1. Увійти в AWS Console → IAM

# 2. Створити групу "Developers" з політиками:
#    - AmazonEC2FullAccess
#    - AmazonRDSFullAccess
#    - AmazonECRFullAccess
#    - AmazonECS_FullAccess
#    - CloudWatchLogsFullAccess

# 3. Створити користувача для CI/CD:
#    - Username: zvit-deploy
#    - Access type: Programmatic access
#    - Додати до групи "Developers"
#    - Зберегти Access Key ID та Secret Access Key
```

### 2.3 Встановлення AWS CLI

```bash
# macOS
brew install awscli

# Linux
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Налаштування
aws configure
# AWS Access Key ID: [ваш ключ]
# AWS Secret Access Key: [ваш секрет]
# Default region name: eu-central-1  (або інший близький регіон)
# Default output format: json
```

---

## 3. Розгортання на AWS

### Варіант A: AWS ECS (Elastic Container Service) - Рекомендовано

#### 3.A.1 Створення ECR репозиторію

```bash
# Створити репозиторій для Docker образів
aws ecr create-repository \
    --repository-name zvit-backend \
    --region eu-central-1

# Отримати URI репозиторію
ECR_URI=$(aws ecr describe-repositories \
    --repository-names zvit-backend \
    --query 'repositories[0].repositoryUri' \
    --output text)

echo "ECR URI: $ECR_URI"
```

#### 3.A.2 Завантаження образу в ECR

```bash
# Авторизація в ECR
aws ecr get-login-password --region eu-central-1 | \
    docker login --username AWS --password-stdin $ECR_URI

# Збірка та тегування
docker build -t zvit-backend:latest .
docker tag zvit-backend:latest $ECR_URI:latest
docker tag zvit-backend:latest $ECR_URI:v1.2.0

# Завантаження
docker push $ECR_URI:latest
docker push $ECR_URI:v1.2.0
```

#### 3.A.3 Створення RDS PostgreSQL

```bash
# Створення Security Group для RDS
aws ec2 create-security-group \
    --group-name zvit-rds-sg \
    --description "Security group for ZVIT RDS"

# Дозволити доступ з ECS
aws ec2 authorize-security-group-ingress \
    --group-name zvit-rds-sg \
    --protocol tcp \
    --port 5432 \
    --source-group zvit-ecs-sg

# Створення RDS інстансу
aws rds create-db-instance \
    --db-instance-identifier zvit-db \
    --db-instance-class db.t3.micro \
    --engine postgres \
    --engine-version 15 \
    --master-username zvit_admin \
    --master-user-password "$(openssl rand -base64 32)" \
    --allocated-storage 20 \
    --vpc-security-group-ids sg-xxxxxxxxx \
    --db-name zvit \
    --backup-retention-period 7 \
    --storage-encrypted \
    --no-publicly-accessible

# Отримати endpoint
aws rds describe-db-instances \
    --db-instance-identifier zvit-db \
    --query 'DBInstances[0].Endpoint.Address' \
    --output text
```

#### 3.A.4 Створення ECS Cluster

```bash
# Створити кластер
aws ecs create-cluster --cluster-name zvit-cluster

# Створити Task Definition (зберегти як task-definition.json)
```

**task-definition.json:**
```json
{
  "family": "zvit-backend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "arn:aws:iam::ACCOUNT_ID:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "zvit-backend",
      "image": "ACCOUNT_ID.dkr.ecr.eu-central-1.amazonaws.com/zvit-backend:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"}
      ],
      "secrets": [
        {
          "name": "DATABASE_URL",
          "valueFrom": "arn:aws:secretsmanager:eu-central-1:ACCOUNT_ID:secret:zvit/database-url"
        },
        {
          "name": "DATABASE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:eu-central-1:ACCOUNT_ID:secret:zvit/database-password"
        },
        {
          "name": "JWT_SECRET",
          "valueFrom": "arn:aws:secretsmanager:eu-central-1:ACCOUNT_ID:secret:zvit/jwt-secret"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/zvit-backend",
          "awslogs-region": "eu-central-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "wget --spider -q http://localhost:8080/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

```bash
# Реєстрація Task Definition
aws ecs register-task-definition --cli-input-json file://task-definition.json

# Створення сервісу
aws ecs create-service \
    --cluster zvit-cluster \
    --service-name zvit-backend-service \
    --task-definition zvit-backend \
    --desired-count 1 \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[subnet-xxx,subnet-yyy],securityGroups=[sg-xxx],assignPublicIp=ENABLED}"
```

#### 3.A.5 Створення Application Load Balancer

```bash
# Створити ALB
aws elbv2 create-load-balancer \
    --name zvit-alb \
    --subnets subnet-xxx subnet-yyy \
    --security-groups sg-xxx \
    --scheme internet-facing \
    --type application

# Створити Target Group
aws elbv2 create-target-group \
    --name zvit-tg \
    --protocol HTTP \
    --port 8080 \
    --vpc-id vpc-xxx \
    --target-type ip \
    --health-check-path /actuator/health

# Створити Listener
aws elbv2 create-listener \
    --load-balancer-arn arn:aws:elasticloadbalancing:... \
    --protocol HTTPS \
    --port 443 \
    --certificates CertificateArn=arn:aws:acm:... \
    --default-actions Type=forward,TargetGroupArn=arn:aws:elasticloadbalancing:...
```

### Варіант B: EC2 (простіший, дешевший для початку)

#### 3.B.1 Створення EC2 інстансу

```bash
# Створити Security Group
aws ec2 create-security-group \
    --group-name zvit-ec2-sg \
    --description "ZVIT EC2 Security Group"

# Дозволити SSH, HTTP, HTTPS
aws ec2 authorize-security-group-ingress \
    --group-name zvit-ec2-sg \
    --protocol tcp --port 22 --cidr 0.0.0.0/0

aws ec2 authorize-security-group-ingress \
    --group-name zvit-ec2-sg \
    --protocol tcp --port 80 --cidr 0.0.0.0/0

aws ec2 authorize-security-group-ingress \
    --group-name zvit-ec2-sg \
    --protocol tcp --port 443 --cidr 0.0.0.0/0

# Створити ключову пару
aws ec2 create-key-pair \
    --key-name zvit-key \
    --query 'KeyMaterial' \
    --output text > zvit-key.pem

chmod 400 zvit-key.pem

# Запустити EC2 (Amazon Linux 2023, t3.small)
aws ec2 run-instances \
    --image-id ami-0c55b159cbfafe1f0 \
    --instance-type t3.small \
    --key-name zvit-key \
    --security-groups zvit-ec2-sg \
    --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=zvit-backend}]'
```

#### 3.B.2 Налаштування EC2

```bash
# Підключитися до EC2
ssh -i zvit-key.pem ec2-user@<EC2_PUBLIC_IP>

# Встановити Docker
sudo yum update -y
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user

# Встановити Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Вийти і зайти знову для застосування груп
exit
ssh -i zvit-key.pem ec2-user@<EC2_PUBLIC_IP>
```

#### 3.B.3 Розгортання на EC2

```bash
# Створити директорію
mkdir -p /home/ec2-user/zvit
cd /home/ec2-user/zvit

# Створити .env файл
cat > .env << 'EOF'
DATABASE_URL=jdbc:postgresql://localhost:5432/zvit
DATABASE_USERNAME=zvit
DATABASE_PASSWORD=your_secure_password_here
JWT_SECRET=your_jwt_secret_here_min_256_bits
ENCRYPTION_KEY=your_encryption_key_here
EOF

# Створити docker-compose.prod.yml
cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  backend:
    image: your-ecr-uri/zvit-backend:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    env_file:
      - .env
    volumes:
      - ./logs:/app/logs
    restart: always
    depends_on:
      - postgres

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: zvit
      POSTGRES_USER: zvit
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: always

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./ssl:/etc/nginx/ssl:ro
    depends_on:
      - backend
    restart: always

volumes:
  postgres_data:
EOF

# Запустити
docker-compose up -d
```

---

## 4. Налаштування домену та SSL

### 4.1 Реєстрація домену

1. Перейти в AWS Route 53
2. Registered domains → Register Domain
3. Ввести бажане ім'я (наприклад: zvit-api.com)
4. Оплатити (~12$/рік для .com)

### 4.2 Налаштування DNS

```bash
# Створити Hosted Zone (якщо ще немає)
aws route53 create-hosted-zone \
    --name zvit-api.com \
    --caller-reference $(date +%s)

# Додати A-запис для ALB або EC2
aws route53 change-resource-record-sets \
    --hosted-zone-id ZXXXXXXXXXXXXX \
    --change-batch '{
        "Changes": [{
            "Action": "CREATE",
            "ResourceRecordSet": {
                "Name": "api.zvit-api.com",
                "Type": "A",
                "AliasTarget": {
                    "HostedZoneId": "ALB_HOSTED_ZONE_ID",
                    "DNSName": "zvit-alb-xxxxx.eu-central-1.elb.amazonaws.com",
                    "EvaluateTargetHealth": true
                }
            }
        }]
    }'
```

### 4.3 SSL сертифікат (AWS Certificate Manager)

```bash
# Запросити сертифікат
aws acm request-certificate \
    --domain-name api.zvit-api.com \
    --validation-method DNS \
    --subject-alternative-names "*.zvit-api.com"

# Отримати CNAME для валідації
aws acm describe-certificate \
    --certificate-arn arn:aws:acm:eu-central-1:xxx:certificate/xxx

# Додати CNAME запис в Route 53 для валідації
# (AWS може зробити це автоматично якщо домен в Route 53)
```

### 4.4 Nginx конфігурація (для EC2)

```nginx
# nginx.conf
events {
    worker_connections 1024;
}

http {
    upstream backend {
        server backend:8080;
    }

    # Redirect HTTP to HTTPS
    server {
        listen 80;
        server_name api.zvit-api.com;
        return 301 https://$server_name$request_uri;
    }

    # HTTPS server
    server {
        listen 443 ssl http2;
        server_name api.zvit-api.com;

        ssl_certificate /etc/nginx/ssl/fullchain.pem;
        ssl_certificate_key /etc/nginx/ssl/privkey.pem;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
        ssl_prefer_server_ciphers off;

        # Security headers
        add_header Strict-Transport-Security "max-age=63072000" always;
        add_header X-Frame-Options DENY;
        add_header X-Content-Type-Options nosniff;

        location / {
            proxy_pass http://backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        location /actuator/health {
            proxy_pass http://backend;
            access_log off;
        }
    }
}
```

---

## 5. Моніторинг та обслуговування

### 5.1 CloudWatch Logs

```bash
# Створити лог групу
aws logs create-log-group --log-group-name /ecs/zvit-backend

# Налаштувати retention
aws logs put-retention-policy \
    --log-group-name /ecs/zvit-backend \
    --retention-in-days 30
```

### 5.2 CloudWatch Alarms

```bash
# Алерт на високий CPU
aws cloudwatch put-metric-alarm \
    --alarm-name zvit-high-cpu \
    --alarm-description "CPU > 80%" \
    --metric-name CPUUtilization \
    --namespace AWS/ECS \
    --statistic Average \
    --period 300 \
    --threshold 80 \
    --comparison-operator GreaterThanThreshold \
    --evaluation-periods 2 \
    --alarm-actions arn:aws:sns:eu-central-1:xxx:zvit-alerts

# Алерт на помилки
aws cloudwatch put-metric-alarm \
    --alarm-name zvit-5xx-errors \
    --alarm-description "5xx errors > 10" \
    --metric-name HTTPCode_Target_5XX_Count \
    --namespace AWS/ApplicationELB \
    --statistic Sum \
    --period 60 \
    --threshold 10 \
    --comparison-operator GreaterThanThreshold \
    --evaluation-periods 1 \
    --alarm-actions arn:aws:sns:eu-central-1:xxx:zvit-alerts
```

### 5.3 Скрипт деплою

```bash
#!/bin/bash
# deploy.sh

set -e

VERSION=${1:-latest}
ECR_URI="xxx.dkr.ecr.eu-central-1.amazonaws.com/zvit-backend"

echo "🚀 Deploying ZVIT Backend v$VERSION"

# Login to ECR
aws ecr get-login-password --region eu-central-1 | \
    docker login --username AWS --password-stdin $ECR_URI

# Build and push
echo "📦 Building Docker image..."
docker build -t zvit-backend:$VERSION .
docker tag zvit-backend:$VERSION $ECR_URI:$VERSION
docker tag zvit-backend:$VERSION $ECR_URI:latest

echo "⬆️ Pushing to ECR..."
docker push $ECR_URI:$VERSION
docker push $ECR_URI:latest

# Update ECS service
echo "🔄 Updating ECS service..."
aws ecs update-service \
    --cluster zvit-cluster \
    --service zvit-backend-service \
    --force-new-deployment

echo "✅ Deployment initiated! Check AWS Console for status."
```

### 5.4 Backup бази даних

```bash
# Автоматичний backup в RDS вже налаштований
# Для ручного snapshot:
aws rds create-db-snapshot \
    --db-instance-identifier zvit-db \
    --db-snapshot-identifier zvit-db-manual-$(date +%Y%m%d)
```

---

## Оцінка вартості (AWS, eu-central-1)

| Сервіс | Конфігурація | Ціна/місяць |
|--------|--------------|-------------|
| ECS Fargate | 0.5 vCPU, 1GB RAM | ~$15 |
| RDS PostgreSQL | db.t3.micro | ~$15 |
| ALB | 1 ALB | ~$20 |
| ECR | 1 GB storage | ~$0.10 |
| Route 53 | 1 hosted zone | ~$0.50 |
| CloudWatch | Basic | ~$3 |
| **Всього** | | **~$55/місяць** |

### Економніший варіант (EC2):

| Сервіс | Конфігурація | Ціна/місяць |
|--------|--------------|-------------|
| EC2 | t3.small (Reserved 1yr) | ~$10 |
| EBS | 30 GB | ~$3 |
| Route 53 | 1 hosted zone | ~$0.50 |
| **Всього** | | **~$15/місяць** |

---

## Швидкий старт (копіюй і виконуй)

```bash
# 1. Клонувати репо
git clone https://github.com/your-repo/zvt-backend.git
cd zvt-backend

# 2. Створити Dockerfile (якщо немає)
# Див. розділ 1.1

# 3. Зібрати образ
docker build -t zvit-backend:latest .

# 4. Протестувати локально
docker-compose up -d
curl http://localhost:8080/actuator/health

# 5. Деплой на AWS
./deploy.sh v1.2.0
```
