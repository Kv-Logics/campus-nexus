import { EC2Client, DescribeInstancesCommand } from "@aws-sdk/client-ec2";

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, x-admin-key");

  if (req.method === "OPTIONS") {
    return res.status(200).end();
  }

  const region = process.env.MY_AWS_REGION || "ap-south-1";
  const accessKeyId = process.env.MY_AWS_ACCESS_KEY;
  const secretAccessKey = process.env.MY_AWS_SECRET_KEY;
  const instanceId = process.env.MY_EC2_INSTANCE_ID;

  if (!accessKeyId || !secretAccessKey || !instanceId) {
    return res.status(500).json({
      error: "Missing AWS credentials or instance ID in Vercel environment variables."
    });
  }

  const ec2 = new EC2Client({
    region,
    credentials: { accessKeyId, secretAccessKey }
  });

  try {
    const data = await ec2.send(
      new DescribeInstancesCommand({
        InstanceIds: [instanceId]
      })
    );

    const reservation = data.Reservations?.[0];
    const instance = reservation?.Instances?.[0];

    if (!instance) {
      return res.status(404).json({ error: "Instance not found" });
    }

    return res.status(200).json({
      instanceId,
      region,
      status: instance.State?.Name || "unknown", // running | stopped | pending | stopping
      publicIp: instance.PublicIpAddress || null,
      launchTime: instance.LaunchTime || null
    });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
}
