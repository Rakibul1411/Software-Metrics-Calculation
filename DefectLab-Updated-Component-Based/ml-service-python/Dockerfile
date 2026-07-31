FROM python:3.12-slim
WORKDIR /app
COPY ml-service-python/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ml-service-python/app ./app
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
