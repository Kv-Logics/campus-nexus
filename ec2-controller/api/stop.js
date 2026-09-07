import { EC2Client, StopInstancesCommand } from "@aws-sdk/client-ec2";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, x-admin-key");

  if (req.method === "OPTIONS") {
    return res.status(200).end();
  }

  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed. Use POST." });
  }

  const expectedKey = process.env.ADMIN_SECRET_KEY;
  const providedKey = req.headers["x-admin-key"] || req.query.key;

  if (expectedKey && providedKey !== expectedKey) {
    return res.status(401).json({ error: "Unauthorized: Invalid Admin Key" });
  }

  const region = process.env.MY_AWS_REGION || "ap-south-1";
  const accessKeyId = process.env.MY_AWS_ACCESS_KEY;
  const secretAccessKey = process.env.MY_AWS_SECRET_KEY;
  const instanceId = process.env.MY_EC2_INSTANCE_ID;

  if (!accessKeyId || !secretAccessKey || !instanceId) {
    return res.status(500).json({ error: "Missing AWS configuration" });
  }

  const ec2 = new EC2Client({
    region,
    credentials: { accessKeyId, secretAccessKey }
  });

  try {
    const data = await ec2.send(
      new StopInstancesCommand({
        InstanceIds: [instanceId]
      })
    );

    const change = data.StoppingInstances?.[0];
    return res.status(200).json({
      success: true,
      previousState: change?.PreviousState?.Name,
      currentState: change?.CurrentState?.Name
    });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
}
