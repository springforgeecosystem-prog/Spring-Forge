import boto3
import json

# Claude models are available in us-east-1, us-west-2, eu-west-1
bedrock = boto3.client('bedrock-runtime', region_name='us-east-1')


def lambda_handler(event, context):
    try:
        body = json.loads(event.get('body', '{}'))

        # modelId is passed in the body from the plugin — extract it
        # Bedrock API requires modelId as a separate parameter, not in the request body
        model_id = body.pop('modelId', 'us.anthropic.claude-sonnet-4-20250514-v1:0')

        response = bedrock.invoke_model(
            modelId=model_id,
            body=json.dumps(body),
            contentType='application/json',
            accept='application/json'
        )

        return {
            'statusCode': 200,
            'headers': {
                'Content-Type': 'application/json',
                'Access-Control-Allow-Origin': '*'
            },
            'body': response['body'].read().decode('utf-8')
        }

    except Exception as e:
        return {
            'statusCode': 500,
            'headers': {'Content-Type': 'application/json'},
            'body': json.dumps({'error': str(e)})
        }
