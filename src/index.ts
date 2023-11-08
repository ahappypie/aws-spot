#!/usr/bin/env node

import { Command, InvalidArgumentError, Option } from 'commander';
import { textSync } from 'figlet';

const program = new Command();

if (
  process.stdout &&
  process.stdout.isTTY &&
  process.env.TERM !== 'dumb' &&
  !('CI' in process.env)
) {
  console.log(
    textSync('Aws-spot', {
      font: 'Ogre',
      width: 80,
      horizontalLayout: 'fitted'
    })
  );
}

program
  .version(
    process.env.npm_package_version || 'unknown',
    '-v, --version',
    'version number from package.json'
  )
  .description('Tool for querying and moving data from the EC2 Spot Market')
  .option('-l, --location <value>', 'location of output')
  .addOption(new Option('-o, --output <value>', 'output mode').default('file'))
  .addOption(
    new Option(
      '-s --start-time <value>',
      'start time of this request, formatted for Node.js `Date.parse()`. Must be equal or less than 90 days in the past.'
    )
  )
  .addOption(
    new Option(
      '-e --end-time <value>',
      'end time of this request, formatted for Node.js `Date.parse()`. Must not be greater than now. Defaults to now.'
    )
  );

const options = program.opts();

options.location = parseLocation(options.location);

function parseLocation(value: string | undefined) {
  if (value) {
    return value;
  } else if (options.output === 'file' && !value) {
    return 'out';
  } else if (options.output === 's3' && !value) {
    throw new InvalidArgumentError(
      'must specify location when using s3 output mode'
    );
  }
}

import { fill } from './cmd/fill';
program.addCommand(
  new Command('fill').action(async () => {
    await fill({
      outputMode: options.output,
      location: options.location,
      start_time: options.startTime,
      end_time: options.endTime
    });
  })
);

program.parse(process.argv);
