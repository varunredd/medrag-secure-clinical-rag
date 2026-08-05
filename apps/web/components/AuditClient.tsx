"use client";

import { useEffect, useState } from "react";

type AuditEvent = {
  id: string;
  actorId: string;
  actorRoles: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  outcome: string;
  requestId: string;
  occurredAt: string;
};

type AuditPage = {
  content: AuditEvent[];
};

export function AuditClient() {
  const [events, setEvents] = useState<AuditEvent[]>([]);

  useEffect(() => {
    void fetch("/api/backend/api/v1/audit-events?size=100", {
      cache: "no-store",
    })
      .then((response) =>
        response.ok ? response.json() : Promise.reject(new Error("load failed")),
      )
      .then((body: AuditPage) => setEvents(body.content))
      .catch(() => setEvents([]));
  }, []);

  return (
    <section className="panel">
      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Action</th>
              <th>Actor</th>
              <th>Resource</th>
              <th>Outcome</th>
            </tr>
          </thead>
          <tbody>
            {events.map((event) => (
              <tr key={event.id}>
                <td>{new Date(event.occurredAt).toLocaleString()}</td>
                <td>
                  <strong>{event.action}</strong>
                  <small>{event.requestId}</small>
                </td>
                <td>
                  {event.actorId}
                  <small>{event.actorRoles}</small>
                </td>
                <td>
                  {event.resourceType}
                  <small>{event.resourceId}</small>
                </td>
                <td>
                  <span className="status">{event.outcome}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
