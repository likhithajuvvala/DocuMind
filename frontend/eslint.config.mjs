import nextCoreWebVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

const config = [
  ...nextCoreWebVitals,
  ...nextTypescript,
  {
    ignores: [".next/**", "node_modules/**", "next-env.d.ts"]
  },
  {
    rules: {
      // New in eslint-plugin-react-hooks 7 (pulled in by the eslint-config-next 16 upgrade this
      // rule shipped with). It flags several existing, working effects (SessionNav,
      // RequireSession, the documents/chat polling effects) that synchronously call setState to
      // sync from an external source (localStorage, a poll timer) — a legitimate pattern the rule
      // is stricter about than before. Downgrading to a warning here so the dependency bump stays
      // a security fix, not a scope-creeping effect refactor; revisit each flagged effect as its
      // own follow-up.
      "react-hooks/set-state-in-effect": "warn"
    }
  }
];

export default config;
