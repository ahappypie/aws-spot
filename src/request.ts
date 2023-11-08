import { Readable } from 'stream';
import {
  DescribeSpotPriceHistoryCommand,
  EC2Client,
  paginateDescribeSpotPriceHistory
} from '@aws-sdk/client-ec2';

export interface SpotHistoryRequestArgs {
  start_time: Date;
  end_time?: Date;
}
export function spotHistoryRequest(args: SpotHistoryRequestArgs): Readable {
  const client = new EC2Client();

  const input = new DescribeSpotPriceHistoryCommand({
    StartTime: args.start_time,
    EndTime: args.end_time,
    ProductDescriptions: ['Linux/UNIX (Amazon VPC)']
  });

  const stream = new Readable();
  requester({
    client,
    stream,
    input
  });

  return stream;
}

interface RequesterArgs {
  client: EC2Client;
  stream: Readable;
  input: DescribeSpotPriceHistoryCommand;
}
function requester(args: RequesterArgs) {
  const history = args.client.send(args.input);
  history.then((out) => {
    if (out.SpotPriceHistory) {
      args.stream.push(out.SpotPriceHistory);
    } else if (out.NextToken) {
      requester({
        client: args.client,
        stream: args.stream,
        input: new DescribeSpotPriceHistoryCommand({ NextToken: out.NextToken })
      });
    } else if (!out.SpotPriceHistory && !out.NextToken) {
      args.stream.destroy();
    }
  });
}
