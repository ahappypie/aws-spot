import pino from 'pino';

export const logger = pino({
  name: 'aws-spot',
  level: 'debug'
});
