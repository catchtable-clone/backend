#!/bin/bash
# Let's Encrypt 인증서 최초 발급 스크립트 (EC2에서 1회 실행)
#
# 실행 전 사전 조건:
#   1) DNS A 레코드: api.catcheat.kro.kr → EC2 탄력적 IP (전파 완료)
#   2) Security Group: 80, 443 오픈
#   3) 작업 디렉토리에 docker-compose.prod.yml(이름: docker-compose.yml로) 존재
#   4) Docker, Docker Compose 설치 완료
#
# 사용법:
#   chmod +x init-letsencrypt.sh
#   sudo ./init-letsencrypt.sh

set -e

DOMAIN="api.catcheat.kro.kr"
EMAIL="jaebeom7923@gmail.com"
STAGING=0  # 1로 바꾸면 Let's Encrypt 스테이징(테스트) 환경 사용 — rate limit 회피
DATA_PATH="./certbot"

if ! [ -x "$(command -v docker)" ]; then
  echo "❌ Docker가 설치되어 있지 않습니다." >&2
  exit 1
fi

if [ -d "$DATA_PATH/conf/live/$DOMAIN" ]; then
  read -p "기존 인증서가 발견되었습니다. 덮어쓰시겠습니까? (y/N) " decision
  if [ "$decision" != "Y" ] && [ "$decision" != "y" ]; then
    echo "취소합니다."
    exit
  fi
fi

# 1. TLS 다운로드 (Mozilla 권장 옵션)
if [ ! -e "$DATA_PATH/conf/options-ssl-nginx.conf" ] || [ ! -e "$DATA_PATH/conf/ssl-dhparams.pem" ]; then
  echo "▶ Let's Encrypt 권장 SSL 옵션 다운로드..."
  mkdir -p "$DATA_PATH/conf"
  curl -s https://raw.githubusercontent.com/certbot/certbot/master/certbot-nginx/certbot_nginx/_internal/tls_configs/options-ssl-nginx.conf > "$DATA_PATH/conf/options-ssl-nginx.conf"
  curl -s https://raw.githubusercontent.com/certbot/certbot/master/certbot/certbot/ssl-dhparams.pem > "$DATA_PATH/conf/ssl-dhparams.pem"
fi

# 2. 임시(자체 서명) 인증서 생성 — Nginx가 SSL 블록으로 부팅 가능하도록
echo "▶ 임시 더미 인증서 생성: $DOMAIN"
CERT_PATH="/etc/letsencrypt/live/$DOMAIN"
mkdir -p "$DATA_PATH/conf/live/$DOMAIN"
sudo docker compose run --rm --entrypoint "\
  openssl req -x509 -nodes -newkey rsa:4096 -days 1 \
    -keyout '$CERT_PATH/privkey.pem' \
    -out '$CERT_PATH/fullchain.pem' \
    -subj '/CN=localhost'" certbot

# 3. Nginx 시작 (임시 인증서로)
echo "▶ Nginx 시작..."
sudo docker compose up --force-recreate -d nginx

# 4. 임시 인증서 삭제
echo "▶ 임시 인증서 삭제..."
sudo docker compose run --rm --entrypoint "\
  rm -rf /etc/letsencrypt/live/$DOMAIN && \
  rm -rf /etc/letsencrypt/archive/$DOMAIN && \
  rm -rf /etc/letsencrypt/renewal/$DOMAIN.conf" certbot

# 5. 진짜 인증서 발급
echo "▶ Let's Encrypt 인증서 발급 요청..."
STAGING_ARG=""
if [ "$STAGING" != "0" ]; then STAGING_ARG="--staging"; fi

sudo docker compose run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    $STAGING_ARG \
    --email $EMAIL \
    -d $DOMAIN \
    --rsa-key-size 4096 \
    --agree-tos \
    --force-renewal \
    --non-interactive" certbot

# 6. Nginx reload
echo "▶ Nginx reload..."
sudo docker compose exec nginx nginx -s reload

echo ""
echo "✅ 인증서 발급 완료!"
echo "    https://$DOMAIN/actuator/health 확인해 보세요."
