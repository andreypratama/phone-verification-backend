#!/usr/bin/env node

/**
 * Trusted API client for the canonical X-Signature scheme.
 *
 * Usage:
 *   API_HMAC_SECRET='...' node scripts/hmac-request.js \
 *     POST http://localhost:8080/api/v1/verifications \
 *     '{"phoneNumber":"081234567890"}'
 *
 *   API_HMAC_SECRET='...' node scripts/hmac-request.js \
 *     GET http://localhost:8080/api/v1/verifications/<id>/status
 */

const crypto = require('node:crypto');

function usage(message) {
  if (message) console.error(message);
  console.error('Usage: API_HMAC_SECRET=<secret> node scripts/hmac-request.js METHOD URL [JSON_BODY]');
  process.exit(2);
}

const [, , methodArgument, urlArgument, bodyArgument = ''] = process.argv;
if (!methodArgument || !urlArgument) usage();

const secret = process.env.API_HMAC_SECRET;
if (!secret || secret.length < 32) {
  usage('API_HMAC_SECRET must contain at least 32 characters.');
}

let url;
try {
  url = new URL(urlArgument);
} catch (error) {
  usage(`Invalid URL: ${error.message}`);
}

const method = methodArgument.toUpperCase();
const body = bodyArgument;
const timestamp = Math.floor(Date.now() / 1000).toString();
const nonce = crypto.randomUUID();
const requestTarget = `${url.pathname}${url.search}`;
const bodyHash = crypto.createHash('sha256').update(body, 'utf8').digest('hex');
const canonical = `${method}\n${requestTarget}\n${timestamp}\n${nonce}\n${bodyHash}`;
const signature = crypto.createHmac('sha256', secret).update(canonical, 'utf8').digest('hex');

const headers = {
  Accept: 'application/json',
  'X-Timestamp': timestamp,
  'X-Nonce': nonce,
  'X-Signature': signature,
};
if (body.length > 0) {
  headers['Content-Type'] = 'application/json';
}

fetch(url, {
  method,
  headers,
  body: body.length > 0 ? body : undefined,
})
  .then(async (response) => {
    const responseText = await response.text();
    console.log(`HTTP ${response.status}`);
    if (responseText) {
      try {
        console.log(JSON.stringify(JSON.parse(responseText), null, 2));
      } catch {
        console.log(responseText);
      }
    }
    if (!response.ok) process.exitCode = 1;
  })
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
