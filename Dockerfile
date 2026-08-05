# ETAPA 1: Compilação
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Copia todos os arquivos da sua pasta para o container
COPY . .
RUN mkdir bin

# Compila todos os arquivos .java da raiz usando o jar do sqlite que está na raiz
RUN javac -cp "sqlite-jdbc.jar" -d bin *.java

# ETAPA 2: Execução
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copia a pasta com as classes compiladas e o driver do SQLite
COPY --from=builder /build/bin /app/bin
COPY --from=builder /build/sqlite-jdbc.jar /app/sqlite-jdbc.jar

# Cria a pasta segura para o banco de dados
RUN mkdir -p /app/data

EXPOSE 4000

# Executa o jogo incluindo a pasta bin e o arquivo jar no classpath
CMD ["java", "-cp", "bin:sqlite-jdbc.jar", "ServidorMUD"]