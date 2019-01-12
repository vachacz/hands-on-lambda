import boto3
import botocore
import tarfile
import logging
import os

from io import BytesIO
from concurrent.futures import ThreadPoolExecutor

logger = logging.getLogger()
logger.setLevel(logging.INFO)

s3_client = boto3.client('s3')

def untar_s3_file(event, context):
    logger.info('Starting TAR extraction lambda')

    bucket = event['Records'][0]['s3']['bucket']['name']
    key = event['Records'][0]['s3']['object']['key']

    try:
        input_tar_file = s3_client.get_object(Bucket = bucket, Key = key)
        input_tar_content = input_tar_file['Body'].read()

        executor = ThreadPoolExecutor(max_workers = 10)

        with tarfile.open(fileobj = BytesIO(input_tar_content)) as tar:
            for tar_resource in tar:
                if (tar_resource.isfile() and tar_resource.name.endswith('.xml.gz')):
                    inner_file_bytes = tar.extractfile(tar_resource).read()
                    executor.submit(upload_file_to_s3, inner_file_bytes, os.environ['STAGING_FOLDER'], tar_resource.name)

        executor.shutdown(wait = True)

    except Exception as e:
        logger.error(e)
        raise e

    logger.info('Stopping TAR extraction lambda')
    return { 'bucket' : bucket, 'key-source' : key, 'key-untarred' : 'staging' }

def upload_file_to_s3(bytes_content, bucket, key):
    s3_client.upload_fileobj(BytesIO(bytes_content), Bucket = bucket, Key = key)
