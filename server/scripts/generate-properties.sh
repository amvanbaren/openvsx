#!/bin/bash

INSIDE_DOCKER=false
if [[ $* == *--docker* ]]
then
    INSIDE_DOCKER=true;
fi

# This script must be run from the 'server' directory
export OVSX_APP_PROFILE=$PWD/src/dev/resources/application-ovsx.properties

# Clear the content of the ovsx application profile
echo "# Generated by scripts/generate-properties.sh" > $OVSX_APP_PROFILE

if [ "$INSIDE_DOCKER" = true ]
then
    # Set the Elasticsearch host
    echo "ovsx.elasticsearch.host=elasticsearch:9200" >> $OVSX_APP_PROFILE

    # Set the Postgres host
    echo "spring.datasource.url=jdbc:postgresql://postgres:5432/postgres" >> $OVSX_APP_PROFILE
    echo "spring.datasource.username=openvsx" >> $OVSX_APP_PROFILE
    echo "spring.datasource.password=openvsx" >> $OVSX_APP_PROFILE
else
    # Set the Elasticsearch host
    echo "ovsx.elasticsearch.host=localhost:9200" >> $OVSX_APP_PROFILE
fi

# Set the web UI URL
if command -v gp > /dev/null
then
    echo "Using web frontend in Gitpod: `gp url 3000`"
    echo "ovsx.webui.url=`gp url 3000`" >> $OVSX_APP_PROFILE
else
    echo "Using web frontend on local machine: http://localhost:3000"
    echo "ovsx.webui.url=http://localhost:3000" >> $OVSX_APP_PROFILE
fi

# Set the GitHub OAuth client id and client secret
echo "spring.security.oauth2.client.registration.github.client-id=${GITHUB_CLIENT_ID:-none}" >> $OVSX_APP_PROFILE
echo "spring.security.oauth2.client.registration.github.client-secret=${GITHUB_CLIENT_SECRET:-none}" >> $OVSX_APP_PROFILE
if [ -n "$GITHUB_CLIENT_ID" ] && [ -n "$GITHUB_CLIENT_SECRET" ]
then
    echo "GitHub OAuth is enabled."
fi

# Set the Eclipse OAuth client id and client secret
echo "spring.security.oauth2.client.registration.eclipse.client-id=${ECLIPSE_CLIENT_ID:-none}" >> $OVSX_APP_PROFILE
echo "spring.security.oauth2.client.registration.eclipse.client-secret=${ECLIPSE_CLIENT_SECRET:-none}" >> $OVSX_APP_PROFILE
if [ -n "$ECLIPSE_CLIENT_ID" ] && [ -n "$ECLIPSE_CLIENT_SECRET" ]
then
    echo "ovsx.eclipse.publisher-agreement.version=1" >> $OVSX_APP_PROFILE
    echo "ovsx.publishing.require-license=true" >> $OVSX_APP_PROFILE
    echo "Eclipse OAuth is enabled."
fi

# Set the Google Cloud Storage project id and bucket id
if [ -n "$GCP_PROJECT_ID" ] && [ -n "$GCS_BUCKET_ID" ]
then
    echo "ovsx.storage.gcp.project-id=$GCP_PROJECT_ID" >> $OVSX_APP_PROFILE
    echo "ovsx.storage.gcp.bucket-id=$GCS_BUCKET_ID" >> $OVSX_APP_PROFILE
    echo "Using Google Cloud Storage: https://storage.googleapis.com/$GCS_BUCKET_ID/"
fi

# Set the Azure Blob Storage service endpoint and sas token
if [ -n "$AZURE_SERVICE_ENDPOINT" ] && [ -n "$AZURE_SAS_TOKEN" ]
then
    echo "ovsx.storage.azure.service-endpoint=$AZURE_SERVICE_ENDPOINT" >> $OVSX_APP_PROFILE
    echo "ovsx.storage.azure.sas-token=$AZURE_SAS_TOKEN" >> $OVSX_APP_PROFILE
    echo "Using Azure Blob Storage: $AZURE_SERVICE_ENDPOINT"
fi

# Set the Azure Logs Storage service endpoint and sas token
if [ -n "$AZURE_LOGS_SERVICE_ENDPOINT" ] && [ -n "$AZURE_LOGS_SAS_TOKEN" ]
then
    echo "ovsx.logs.azure.service-endpoint=$AZURE_LOGS_SERVICE_ENDPOINT" >> $OVSX_APP_PROFILE
    echo "ovsx.logs.azure.sas-token=$AZURE_LOGS_SAS_TOKEN" >> $OVSX_APP_PROFILE
    echo "Using Azure Logs Storage: $AZURE_LOGS_SERVICE_ENDPOINT"
fi

# Set the AWS Storage service access key id, secret access key, region and endpoint
if [ -n "$AWS_ACCESS_KEY_ID" ] && [ -n "$AWS_SECRET_ACCESS_KEY" ] && [ -n "$AWS_REGION" ] && [ -n "$AWS_BUCKET" ]
then
  echo "ovsx.storage.aws.access-key-id=$AWS_ACCESS_KEY_ID" >> $OVSX_APP_PROFILE
  echo "ovsx.storage.aws.secret-access-key=$AWS_SECRET_ACCESS_KEY" >> $OVSX_APP_PROFILE
  echo "ovsx.storage.aws.region=$AWS_REGION" >> $OVSX_APP_PROFILE
  echo "ovsx.storage.aws.bucket=$AWS_BUCKET" >> $OVSX_APP_PROFILE
  if [ -n "$AWS_PATH_STYLE_ACCESS" ]
  then
    echo "ovsx.storage.aws.path-style-access=$AWS_PATH_STYLE_ACCESS" >> $OVSX_APP_PROFILE
  fi
  if [ -n "$AWS_SERVICE_ENDPOINT" ]
  then
    echo "ovsx.storage.aws.service-endpoint=$AWS_SERVICE_ENDPOINT" >> $OVSX_APP_PROFILE
    echo "Using AWS S3 Storage: $AWS_SERVICE_ENDPOINT"
  else
    echo "Using AWS S3 Storage."
  fi
fi
