cd "$(dirname "$0")" || exit

export WORKSPACE=$(pwd)
export BUILD_ID=5.0.0-SNAPSHOT
export TAG=5.1.1-graph

mvn clean install -f fhir-examples -DskipTests
mvn clean install -f fhir-parent -DskipTests
docker build fhir-install -t cortexcr.azurecr.io/linuxforhealth/fhir-server:${TAG} --platform=linux/amd64
docker push cortexcr.azurecr.io/linuxforhealth/fhir-server:${TAG}

cd ${WORKSPACE}/fhir-install/src/main/docker/fhir-term-graph-loader/
mkdir -p target/
cp ${WORKSPACE}/term/fhir-term-graph-loader/target/fhir-term-graph-loader-*-cli.jar target/
cp ${WORKSPACE}/LICENSE target/
docker build --build-arg FHIR_VERSION=${BUILD_ID} --platform=linux/amd64 -t cortexcr.azurecr.io/linuxforhealth/fhir-term-loader:${TAG} .
docker push cortexcr.azurecr.io/linuxforhealth/fhir-term-loader:${TAG}