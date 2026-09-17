export async function onRequest(context) {
  const { request } = context;
  const { env } = context;

  // 1. Method check: only POST allowed
  if (request.method !== "POST") {
    return new Response(
      JSON.stringify({ ok: false, error: "METHOD_NOT_ALLOWED" }),
      {
        status: 405,
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  // 2. Authentication: check Authorization header
  const authHeader = request.headers.get("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return new Response(
      JSON.stringify({ ok: false, error: "UNAUTHORIZED" }),
      {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  const token = authHeader.replace("Bearer ", "");
  const wakeToken = env.WPP_WAKE_TOKEN;
  if (token !== wakeToken) {
    return new Response(
      JSON.stringify({ ok: false, error: "UNAUTHORIZED" }),
      {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  // 3. Validate configuration
  const healthUrl = env.WPP_HEALTH_URL;
  if (!healthUrl) {
    return new Response(
      JSON.stringify({ ok: false, error: "CONFIGURATION_ERROR" }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  // 4. Call WPP health endpoint
  let upstreamStatus = 0;
  let upok = false;
  try {
    const upstreamResponse = await fetch(healthUrl, {
      method: "GET",
    });
    upstreamStatus = upstreamResponse.status;
    upok = upstreamResponse.ok;
  } catch (err) {
    return new Response(
      JSON.stringify({ ok: false, error: "WPP_WAKE_REQUEST_FAILED" }),
      {
        status: 502,
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  // 5. Return response based on upstream status
  if (upok && upstreamStatus === 200) {
    return new Response(
      JSON.stringify({ ok: true, upstreamStatus: 200 }),
      {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }
    );
  } else {
    return new Response(
      JSON.stringify({ ok: false, upstreamStatus }),
      {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }
    );
  }
}