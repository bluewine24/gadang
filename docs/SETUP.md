# SQL

cd Backend\src\main\resources
mysql -u ssafy -pssafy --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS gadang DEFAULT CHARSET utf8mb4;"

# BE

cd C:\SSAFY\pjt13_buk03_15_04\Backend
.\mvnw.cmd clean spring-boot:run "-Dspring-boot.run.profiles=local"

# FE

cd C:\SSAFY\pjt13_buk03_15_04\frontend
pnpm install
pnpm dev

# AI

cd C:\SSAFY\pjt13_buk03_15_04\ai-server
.\.venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --port 8000
