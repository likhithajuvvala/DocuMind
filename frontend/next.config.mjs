const nextConfig = {
  reactStrictMode: true,
  // "standalone" is what the Dockerfile's runtime stage copies out of .next/standalone to run
  // as `node server.js` — but it changes the build's output layout in a way that breaks Vercel's
  // own tracing/bundling step (ENOENT on next-server.js.nft.json). Vercel sets $VERCEL during
  // its build, so only apply it for the self-hosted (Docker/local) build path.
  output: process.env.VERCEL ? undefined : "standalone",
  env: {
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"
  }
};

export default nextConfig;
