import express from 'express';
import {handleRequest} from './APIRoute';
import ImmaTeapot from './ImmaTeapot';

const serverless = require('serverless-http');
const bodyParser = require('body-parser');
const application = express();

application.use(bodyParser.json({strict: false}));
application.use(bodyParser.urlencoded({extended: true}));
application.get('/test', (request, response) => {
  handleRequest()
    .subscribe(item => {
        console.log(item);
        response.write(JSON.stringify(item));
      }, error => {

      }, () => {
        response.end();
      },
    );
});

application.use((request, response) => {
  response.status(418).send(ImmaTeapot);
});

module.exports.handler = serverless(application);
