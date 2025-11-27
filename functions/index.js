import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import axios from "axios";

admin.initializeApp();

exports.sendEmergencyEmail = functions.database
  .ref("/emergency_alerts/{alertId}")
  .onCreate(async (snapshot, context) => {
    const alert = snapshot.val();
    const contacts = alert.contacts || [];

    const resendKey = functions.config().resend.key;

    for (const contact of contacts) {
      if (!contact.email) continue;

      await axios.post(
        "https://api.resend.com/emails",
        {
          from: "Emergency Alert <alerts@yourdomain.com>",
          to: contact.email,
          subject: "🚨 Emergency Alert",
          text: alert.message,
        },
        {
          headers: {
            Authorization: `Bearer ${resendKey}`,
          },
        }
      );
    }

    return true;
  });
