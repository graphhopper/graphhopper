FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /graphhopper

COPY . .

RUN mvn install -DskipTests

FROM eclipse-temurin:21.0.1_12-jre

# 7-8GB has determined to be the minimum required for import
ENV JAVA_OPTS="-Xmx10g -Xms8g"

RUN mkdir -p /data

# install aws cli
# RUN apt-get update && apt-get install -y curl unzip && \
#     curl "https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip" -o "awscliv2.zip" && \
#     unzip awscliv2.zip && \
#     ./aws/install && \
#     rm -rf awscliv2.zip

WORKDIR /graphhopper

COPY --from=build /graphhopper/web/target/graphhopper*.jar ./

COPY graphhopper.sh config-example.yml config-rs.yml ./

# Enable connections from outside of the container
RUN sed -i '/^ *bind_host/s/^ */&# /p' config-example.yml

VOLUME [ "/data" ]

# Fetch data from s3
# RUN /usr/local/bin/aws s3 cp s3://471112541871-ridesense-routing-data/updated_osm_file_20241121_225129.pbf /data/updated_osm_file_20241121_225129.pbf

# Presigned URL, 
# RUN curl "https://471112541871-ridesense-routing-data.s3.ap-south-1.amazonaws.com/updated_osm_file_20241121_225129.pbf?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEC8aCmFwLXNvdXRoLTEiSDBGAiEA4dvPTM3I3R8L%2BNVS%2BX2tosOguiPx1JHKgTmXrr%2BhDiICIQCMTzvZIlBMPjoJqClTEMVmHaqACzQwGpbMVbkuczoDiSrkAwgYEAAaDDQ3MTExMjU0MTg3MSIMBr%2BWjF%2FKEGhwXymhKsEDI6wJ0VzO6d1hyNKff2yTZgq5GUnGR9QKMtCGGlzA%2BJ7sWXnhAhiclx5p%2F86qAPxuJapr74Ej3aPsjnv0jFhAuKE3MIKWLmO8y5IpgtaXRdvvttsawc2ds5eyjYmFAr3ZzsR5wBzGjWEW1t8nNEac5wdHOglA%2BrR71LyyyPYdEspF%2BE8wGWViNwa2UlxnpcysKdPIdu72iv11SrZDqCpJ1X4g7KjMONoEkTW0jMrGvRjwZ%2BJ9HBXHTMSHQZjc8F6ykw041CLq579XmkMJAOPU5SivXeXmcJe4aDYpVBYcVoCI5rxRBoklPg698C0rIz%2FxhrTbc%2FlFX8Wy1YRUO1bxl4CIRuC78HvbZCS1dT3nfJSIuWKfO8QBjgv72pBNstpfP0SzwgGr7a6vlIbCaysI%2Fq06v%2Fzwp9QAWlKfYmB08bN%2FylQEuJT6T%2F1lDXD3ROLIWPzn8b282V1N9L1fdCZnLayJDwRhIg0q5miSNHFN0HpuK7eUsSM1UQ6ow%2BI8qfTTe2reGrHXrEVpaZ9rRUMSodJL8ieEJEMN2%2FTC6CqCybKz%2FVNTOdObfsnCBN6Aa5KiADLD1Azft9FfLHUS4HecvtIw0uPiuwY64wIdnPG%2BXfRrMvY81EX7gqKbYfvooTJC1vFZYBbojlQJMUj39seYUqAeQ8WrlVSEaOCyeNsTqvK0BuE54h7SU4bV5EpTIZyvczAyB23kDa0P3ZL5jHESq%2FZZbfesZ5eWXhK0pgPAIO6zQsnqBHHzlRfMiBs8lSN9jfebVKXc7a9RryQuqqugbz4JA2rXLM9%2FRbDMq7rfkDbUZtND1aPep%2Bskwh8jRwUh8c%2F2UdXkyonKs87ITPkig2SsaFew2FHT2wSRIDNAJxtRUJJEGBGB7Z6qjNecIHe68I4yoQMQZ%2FweVeuc1bGCjAAPGDfPl1O9jhGPRAWxUjuj8F6U2e9bgZ%2F8U1WmWQYOxdQtHps1cbP7Hs0SNxvZvbI3LBX5%2BfDMwQ4noikbnPW7muREcyzWiptq0B65cjsghn%2FssXoNPy8ozamrhJRxATlSyCmRJJ0QvemYwL2tNrjzhRCt0zhbnE3Qcdl7&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIAW3MD642XU3UMDKPE%2F20250104%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250104T145716Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=32ff2f3c0f666f981a5863fc27c6f6d855a93196feddf20bc1b2ce185fb50bf7" -o /pbf/updated_osm_file_20241121_225129.pbf

EXPOSE 8989

HEALTHCHECK --interval=5s --timeout=3s CMD curl --fail http://localhost:8989/health || exit 1

ENTRYPOINT [ "./graphhopper.sh", "-c", "config-rs.yml"]
