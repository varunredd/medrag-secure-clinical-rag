import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    evidence_queries: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.QUERY_RATE || 2),
      timeUnit: "1s",
      duration: __ENV.DURATION || "2m",
      preAllocatedVUs: 5,
      maxVUs: 30,
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<2500"],
  },
};

const api = __ENV.API_URL || "http://localhost:8080";
const token = __ENV.ACCESS_TOKEN;
const documentIds = (__ENV.DOCUMENT_IDS || "").split(",").filter(Boolean);

export function setup() {
  if (!token || documentIds.length === 0) {
    throw new Error("ACCESS_TOKEN and comma-separated READY DOCUMENT_IDS are required");
  }
}

export default function () {
  const response = http.post(
    `${api}/api/v1/queries`,
    JSON.stringify({
      question: "Summarize the documented follow-up plan.",
      documentIds,
      topK: 8,
    }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "X-Request-ID": `k6-${__VU}-${__ITER}`,
      },
    },
  );
  check(response, {
    "query succeeds": (value) => value.status === 200,
    "query has citations": (value) => {
      try { return JSON.parse(value.body).citations.length > 0; }
      catch { return false; }
    },
  });
  sleep(0.2);
}
