export type ApiProblem = {
  code?: string;
  detail?: string;
  requestId?: string;
};

export class ClinicalApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly requestId?: string;

  constructor(message: string, status: number, problem: ApiProblem = {}) {
    super(message);
    this.name = "ClinicalApiError";
    this.status = status;
    this.code = problem.code;
    this.requestId = problem.requestId;
  }
}

export async function clinicalFetch(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  const response = await fetch(input, {
    ...init,
    credentials: "same-origin",
    cache: init?.cache ?? "no-store",
  });

  if (response.status === 401) {
    const returnTo = `${window.location.pathname}${window.location.search}`;
    window.location.assign(
      new URL(
        `/api/auth/login?reason=session_expired&returnTo=${encodeURIComponent(returnTo)}`,
        window.location.origin,
      ).toString(),
    );
    throw new ClinicalApiError("Secure session expired", 401, {
      code: "UNAUTHENTICATED",
    });
  }
  return response;
}

export async function apiProblem(response: Response): Promise<ApiProblem> {
  const requestId = response.headers.get("x-request-id") ?? undefined;
  const body = (await response.json().catch(() => ({}))) as ApiProblem;
  return { ...body, requestId: body.requestId ?? requestId };
}

export function supportMessage(problem: ApiProblem, fallback: string): string {
  const message = problem.detail || fallback;
  return problem.requestId
    ? `${message} Support code: ${problem.requestId}`
    : message;
}
