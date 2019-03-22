'use strict';

const functions = require('firebase-functions');

// https://firebase.google.com/docs/functions/write-firebase-functions
exports.helloWorld = functions.https.onRequest((request, response) => {
  response.send("Hello from Firebase!");
});

const admin = require('firebase-admin');
admin.initializeApp();
const db = admin.firestore();

exports.helloWorld = functions.https.onRequest((request, response) => {
  response.send("Hello from Firebase!");
});

/** Changes to the wbb/{boardId} doc mean a config change */
exports.dbToDevice = functions.firestore.document('wbb/{boardId}').onUpdate(async (change, context) => {
  const newValue = change.after.data();
  const previousValue = change.before.data();

  const interestingFields = ['spoolDistanceCm'];
  const interestingChange = interestingFields.some((element, index) => previousValue[index] !== newValue[index]);

  if (interestingChange) {
    const state = interestingFields.reduce((accumulator, fieldName) => {
      accumulator[fieldName] = newValue[fieldName]
    }, {});

    return db.collection('logs').document(context.params.boardId).collection('changes').add(state);
    // TODO: Send to MQTT
  }
});

/**
 * Changes picked up from MQTT should be set on the doc
 * Test with `gcloud pubsub topics publish topic-name --message '{"name":"Xenia"}'`
 * @see https://firebase.google.com/docs/functions/pubsub-events
 */
exports.deviceToDb = functions.pubsub.topic('my-device-events').onPublish((message, context) => {
  // context.timestamp, context.eventId
  //const name = message.json.name;
  return db.collection('telemetry').document(context.params.boardId).add(message.json);
});


/*
exports.helloPubSub = (data, context) => {
  const pubSubMessage = data;
  const name = pubSubMessage.data
    ? Buffer.from(pubSubMessage.data, 'base64').toString()
    : 'World';
  console.log(`Hello, ${name}!`);
};

module.exports = functions.pubsub.topic('device-events').onPublish(async (message) => {
  const deviceId = message.attributes.deviceId;

  // Write the device state into firestore
  const deviceRef = firestore.doc(`devices/${deviceId}`);
  try {
    // Ensure the device is also marked as 'online' when state is updated
    await deviceRef.update({ 'state': message.json, 'online': true });
    console.log(`State updated for ${deviceId}`);
  } catch (error) {
    console.error(error);
  }
});
 */