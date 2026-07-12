FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
WORKDIR /app
RUN addgroup --system gadang && adduser --system --ingroup gadang gadang
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY --chown=gadang:gadang . .
USER gadang
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
