import { Transform, TransformCallback, TransformOptions } from 'stream';
import { DescribeSpotPriceHistoryCommandOutput } from '@aws-sdk/client-ec2';

const EOL = '\n';

export class ObjectToNDJSON extends Transform {
  constructor(opts?: TransformOptions) {
    super({ ...opts, readableObjectMode: false, writableObjectMode: true });
  }

  _transform(chunk: never, _: string, callback: TransformCallback) {
    const s = JSON.stringify(chunk) + EOL;
    this.push(s);
    callback();
  }
}

export class SpotPriceHistoryTransformer extends Transform {
  constructor(opts?: TransformOptions) {
    super({ ...opts, readableObjectMode: true, writableObjectMode: true });
  }

  _transform(
    chunk: DescribeSpotPriceHistoryCommandOutput,
    encoding: BufferEncoding,
    callback: TransformCallback
  ) {
    const history = chunk.SpotPriceHistory;
    if (history) {
      for (const sp of history) {
        this.push(sp);
      }
    }
    callback();
  }
}
