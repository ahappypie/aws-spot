import { logger } from '../logger';
import { ObjectToNDJSON, SpotPriceHistoryTransformer } from '../transform';
import { createGzip } from 'zlib';
import { createWriteStream } from 'fs';
import { Readable } from 'stream';
import { Upload } from '@aws-sdk/lib-storage';
import { S3Client } from '@aws-sdk/client-s3';
import {
  EC2Client,
  paginateDescribeSpotPriceHistory
} from '@aws-sdk/client-ec2';

type OutputMode = 'file' | 's3';

interface FillArgs {
  outputMode: OutputMode;
  location: string;
  start_time: string;
  end_time?: string;
}
export async function fill(args: FillArgs) {
  const start_time = new Date(Date.parse(args.start_time));
  const end_time = args.end_time
    ? new Date(Date.parse(args.end_time))
    : new Date();

  const generator = paginateDescribeSpotPriceHistory(
    {
      client: new EC2Client()
    },
    {
      StartTime: start_time,
      EndTime: end_time,
      ProductDescriptions: ['Linux/UNIX (Amazon VPC)']
    }
  );

  logger.debug(
    {},
    `starting stream with --start-time ${start_time.toISOString()} and --end-time ${end_time.toISOString()}`
  );

  //write
  const stream = Readable.from(generator)
    .pipe(new SpotPriceHistoryTransformer())
    .pipe(new ObjectToNDJSON())
    .pipe(createGzip());

  if (args.outputMode === 'file') {
    stream.pipe(
      createWriteStream(
        `${
          args.location
        }/${start_time.toISOString()}-${end_time.toISOString()}.json.gz`
      )
    );
  } else if (args.outputMode === 's3') {
    const upload = new Upload({
      client: new S3Client(),
      params: {
        Key: `aws/spot/history/${start_time.toISOString()}-${end_time.toISOString()}.json.gz`,
        Bucket: args.location,
        Body: stream
      }
    });

    upload.on('httpUploadProgress', (progress) =>
      logger.debug(
        `upload part ${progress.part} progress ${progress.loaded} of ${progress.total}`
      )
    );

    const result = await upload.done();
    logger.info(`upload complete`);
  }
  //continue
}
