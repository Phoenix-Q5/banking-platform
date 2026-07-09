#!/usr/bin/env python3
"""Generate Harbor Bank Postman collection from endpoint catalog."""

import json
from pathlib import Path

OUTPUT = Path(__file__).with_name("Harbor-Bank-API.postman_collection.json")


def req(name, method, url_path, body=None, query=None, description="", base_var="baseUrl", auth=True):
    """Build a Postman request item."""
    if isinstance(url_path, str):
        path_parts = [p for p in url_path.split("/") if p]
    else:
        path_parts = url_path

    raw = "{{" + base_var + "}}/" + "/".join(path_parts)
    if query:
        raw += "?" + "&".join(f"{k}={v}" for k, v in query.items())

    item = {
        "name": name,
        "request": {
            "method": method,
            "header": [{"key": "Content-Type", "value": "application/json", "type": "text"}],
            "url": {
                "raw": raw,
                "host": [f"{{{{{base_var}}}}}"],
                "path": path_parts,
            },
            "description": description,
        },
    }
    if query:
        item["request"]["url"]["query"] = [
            {"key": k, "value": v, "description": ""} for k, v in query.items()
        ]
    if body is not None:
        item["request"]["body"] = {"mode": "raw", "raw": json.dumps(body, indent=2)}
    if not auth:
        item["request"]["auth"] = {"type": "noauth"}
    return item


def folder(name, items, description=""):
    return {"name": name, "description": description, "item": items}


collection = {
    "info": {
        "name": "Harbor Bank API",
        "description": "Complete Harbor Bank platform API — gateway-backed banking services and ops agent.\n\nImport this collection, run **Auth → Get Access Token (demo.customer)**, then call any endpoint.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "auth": {
        "type": "bearer",
        "bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}],
    },
    "variable": [
        {"key": "baseUrl", "value": "http://localhost:8080"},
        {"key": "opsAgentUrl", "value": "http://localhost:8085"},
        {"key": "keycloakUrl", "value": "http://localhost:8180"},
        {"key": "accessToken", "value": ""},
        {"key": "customerId", "value": "00000000-0000-0000-0000-000000000001"},
        {"key": "accountId", "value": "00000000-0000-0000-0000-000000000010"},
        {"key": "toAccountId", "value": "00000000-0000-0000-0000-000000000011"},
        {"key": "transactionId", "value": "00000000-0000-0000-0000-000000000020"},
        {"key": "paymentId", "value": "00000000-0000-0000-0000-000000000030"},
        {"key": "beneficiaryId", "value": "00000000-0000-0000-0000-000000000031"},
        {"key": "cardId", "value": "00000000-0000-0000-0000-000000000040"},
        {"key": "loanId", "value": "00000000-0000-0000-0000-000000000050"},
        {"key": "notificationId", "value": "00000000-0000-0000-0000-000000000060"},
        {"key": "incidentId", "value": ""},
        {"key": "actionId", "value": ""},
        {"key": "deviceToken", "value": "demo-device-token-001"},
    ],
    "item": [
        folder(
            "Auth",
            [
                {
                    "name": "Get Access Token (demo.customer)",
                    "event": [
                        {
                            "listen": "test",
                            "script": {
                                "type": "text/javascript",
                                "exec": [
                                    "if (pm.response.code === 200) {",
                                    "  const json = pm.response.json();",
                                    "  pm.collectionVariables.set('accessToken', json.access_token);",
                                    "  pm.test('Token saved', () => pm.expect(json.access_token).to.be.a('string'));",
                                    "}",
                                ],
                            },
                        }
                    ],
                    "request": {
                        "auth": {"type": "noauth"},
                        "method": "POST",
                        "header": [{"key": "Content-Type", "value": "application/x-www-form-urlencoded"}],
                        "body": {
                            "mode": "urlencoded",
                            "urlencoded": [
                                {"key": "client_id", "value": "banking-web"},
                                {"key": "grant_type", "value": "password"},
                                {"key": "username", "value": "demo.customer"},
                                {"key": "password", "value": "password"},
                                {"key": "scope", "value": "openid profile email"},
                            ],
                        },
                        "url": {
                            "raw": "{{keycloakUrl}}/realms/banking/protocol/openid-connect/token",
                            "host": ["{{keycloakUrl}}"],
                            "path": ["realms", "banking", "protocol", "openid-connect", "token"],
                        },
                        "description": "Obtain a JWT for demo.customer. Token is saved to `accessToken` collection variable.",
                    },
                },
                {
                    "name": "Get Access Token (demo.admin)",
                    "event": [
                        {
                            "listen": "test",
                            "script": {
                                "type": "text/javascript",
                                "exec": [
                                    "if (pm.response.code === 200) {",
                                    "  pm.collectionVariables.set('accessToken', pm.response.json().access_token);",
                                    "}",
                                ],
                            },
                        }
                    ],
                    "request": {
                        "auth": {"type": "noauth"},
                        "method": "POST",
                        "header": [{"key": "Content-Type", "value": "application/x-www-form-urlencoded"}],
                        "body": {
                            "mode": "urlencoded",
                            "urlencoded": [
                                {"key": "client_id", "value": "banking-web"},
                                {"key": "grant_type", "value": "password"},
                                {"key": "username", "value": "demo.admin"},
                                {"key": "password", "value": "password"},
                                {"key": "scope", "value": "openid profile email"},
                            ],
                        },
                        "url": {
                            "raw": "{{keycloakUrl}}/realms/banking/protocol/openid-connect/token",
                            "host": ["{{keycloakUrl}}"],
                            "path": ["realms", "banking", "protocol", "openid-connect", "token"],
                        },
                    },
                },
            ],
            "Keycloak token endpoints for Harbor Bank demo users.",
        ),
        folder(
            "Accounts",
            [
                req("Create Account", "POST", "api/accounts", {"customerId": "{{customerId}}", "currency": "USD"}),
                req("Get Account", "GET", "api/accounts/{{accountId}}"),
                req("List Accounts by Customer", "GET", "api/accounts", query={"customerId": "{{customerId}}"}),
                req(
                    "Debit Account (internal S2S)",
                    "POST",
                    "api/accounts/{{accountId}}/debit",
                    {"transactionId": "{{transactionId}}", "amount": "25.00"},
                    description="Service-to-service; permitted without JWT on account-service.",
                ),
                req(
                    "Credit Account (internal S2S)",
                    "POST",
                    "api/accounts/{{accountId}}/credit",
                    {"transactionId": "{{transactionId}}", "amount": "25.00"},
                    description="Service-to-service; permitted without JWT on account-service.",
                ),
                req(
                    "Get Account Internal (S2S)",
                    "GET",
                    "api/accounts/internal/{{accountId}}",
                    description="Internal lookup used by transaction-service.",
                ),
            ],
        ),
        folder(
            "Transactions",
            [
                req(
                    "Transfer",
                    "POST",
                    "api/transactions",
                    {
                        "fromAccountId": "{{accountId}}",
                        "toAccountId": "{{toAccountId}}",
                        "amount": "10.00",
                        "currency": "USD",
                    },
                ),
                req("Get Transaction", "GET", "api/transactions/{{transactionId}}"),
                req("List Transactions by Account", "GET", "api/transactions", query={"accountId": "{{accountId}}"}),
            ],
        ),
        folder(
            "Customers",
            [
                req(
                    "Create Customer",
                    "POST",
                    "api/customers",
                    {
                        "externalUserId": "ext-demo-001",
                        "email": "new.customer@example.com",
                        "firstName": "New",
                        "lastName": "Customer",
                        "phone": "+15551234567",
                        "country": "US",
                    },
                ),
                req("Get Customer", "GET", "api/customers/{{customerId}}"),
                req("Search by Email", "GET", "api/customers", query={"email": "demo.customer@example.com"}),
                req("Search by Last Name", "GET", "api/customers", query={"lastName": "Customer"}),
                req("List All Customers", "GET", "api/customers"),
                req(
                    "Update Customer",
                    "PUT",
                    "api/customers/{{customerId}}",
                    {"phone": "+15559876543", "city": "Boston", "state": "MA", "postalCode": "02101", "country": "US"},
                ),
                req("Update KYC", "POST", "api/customers/{{customerId}}/kyc", {"kycStatus": "VERIFIED"}),
                req("Suspend Customer", "POST", "api/customers/{{customerId}}/suspend"),
            ],
        ),
        folder(
            "Payments",
            [
                req(
                    "Create Beneficiary",
                    "POST",
                    "api/payments/beneficiaries",
                    {
                        "customerId": "{{customerId}}",
                        "nickname": "Rent",
                        "accountNumber": "9876543210",
                        "routingNumber": "021000021",
                        "bankName": "Chase",
                        "currency": "USD",
                    },
                ),
                req("List Beneficiaries", "GET", "api/payments/beneficiaries", query={"customerId": "{{customerId}}"}),
                req(
                    "Create Payment",
                    "POST",
                    "api/payments",
                    {
                        "customerId": "{{customerId}}",
                        "fromAccountId": "{{accountId}}",
                        "beneficiaryId": "{{beneficiaryId}}",
                        "paymentType": "ACH",
                        "amount": "150.00",
                        "currency": "USD",
                        "reference": "RENT-APR",
                        "description": "Monthly rent",
                    },
                ),
                req("Get Payment", "GET", "api/payments/{{paymentId}}"),
                req("List Payments by Customer", "GET", "api/payments", query={"customerId": "{{customerId}}"}),
                req("List Payments by Account", "GET", "api/payments", query={"accountId": "{{accountId}}"}),
                req("Cancel Payment", "POST", "api/payments/{{paymentId}}/cancel"),
            ],
        ),
        folder(
            "Cards",
            [
                req(
                    "Issue Card",
                    "POST",
                    "api/cards",
                    {
                        "customerId": "{{customerId}}",
                        "accountId": "{{accountId}}",
                        "cardType": "DEBIT",
                        "cardNetwork": "VISA",
                        "dailyLimit": "1000.00",
                        "monthlyLimit": "10000.00",
                    },
                ),
                req("Get Card", "GET", "api/cards/{{cardId}}"),
                req("List Cards by Customer", "GET", "api/cards", query={"customerId": "{{customerId}}"}),
                req("List Cards by Account", "GET", "api/cards", query={"accountId": "{{accountId}}"}),
                req("Freeze Card", "POST", "api/cards/{{cardId}}/freeze"),
                req("Unfreeze Card", "POST", "api/cards/{{cardId}}/unfreeze"),
                req("Update Card Limits", "PUT", "api/cards/{{cardId}}/limits", {"dailyLimit": "500.00", "monthlyLimit": "5000.00"}),
            ],
        ),
        folder(
            "Loans",
            [
                req(
                    "Apply for Loan",
                    "POST",
                    "api/loans",
                    {
                        "customerId": "{{customerId}}",
                        "productCode": "PERSONAL",
                        "principal": "10000.00",
                        "interestRate": "7.50",
                        "termMonths": 36,
                        "currency": "USD",
                        "purpose": "Home improvement",
                    },
                ),
                req("Get Loan", "GET", "api/loans/{{loanId}}"),
                req("List Loans by Customer", "GET", "api/loans", query={"customerId": "{{customerId}}"}),
                req("List Loans by Status", "GET", "api/loans", query={"status": "APPLIED"}),
                req("Loan Decision", "POST", "api/loans/{{loanId}}/decision", {"decision": "APPROVE"}),
            ],
        ),
        folder(
            "Notifications",
            [
                req(
                    "Create Notification",
                    "POST",
                    "api/notifications",
                    {
                        "customerId": "{{customerId}}",
                        "channel": "IN_APP",
                        "category": "GENERAL",
                        "title": "Hello",
                        "body": "Test notification from Postman",
                    },
                ),
                req("List Notifications", "GET", "api/notifications", query={"customerId": "{{customerId}}"}),
                req("Mark Notification Read", "POST", "api/notifications/{{notificationId}}/read"),
                req(
                    "Register Device",
                    "POST",
                    "api/notifications/devices",
                    {"customerId": "{{customerId}}", "platform": "IOS", "token": "{{deviceToken}}"},
                ),
                req("List Devices", "GET", "api/notifications/devices", query={"customerId": "{{customerId}}"}),
                req("Deactivate Device", "DELETE", "api/notifications/devices/{{deviceToken}}"),
                req(
                    "Publish Service Alert (internal)",
                    "POST",
                    "api/notifications/internal/service-alert",
                    {
                        "title": "Service degraded",
                        "body": "account-service circuit breaker open",
                        "category": "SERVICE",
                        "severity": "CRITICAL",
                        "incidentId": "inc-001",
                        "affectedService": "account-service",
                    },
                    description="Internal S2S endpoint (ops-agent → notification-service). No auth required on notification-service.",
                ),
            ],
        ),
        folder(
            "Audit",
            [
                req(
                    "Create Audit Event",
                    "POST",
                    "api/audit/events",
                    {
                        "actor": "demo.admin",
                        "action": "VIEW",
                        "resourceType": "ACCOUNT",
                        "resourceId": "{{accountId}}",
                        "customerId": "{{customerId}}",
                        "details": "Viewed account in admin console",
                        "ipAddress": "127.0.0.1",
                    },
                ),
                req("List Audit Events (recent)", "GET", "api/audit/events"),
                req("List Audit Events by Customer", "GET", "api/audit/events", query={"customerId": "{{customerId}}"}),
                req(
                    "List Audit Events by Resource",
                    "GET",
                    "api/audit/events",
                    query={"resourceType": "ACCOUNT", "resourceId": "{{accountId}}"},
                ),
            ],
        ),
        folder(
            "Ops Agent",
            [
                req("Health Summary", "GET", "api/agent/health-summary", base_var="opsAgentUrl", auth=False),
                req("List Tools", "GET", "api/agent/tools", base_var="opsAgentUrl", auth=False),
                req(
                    "Investigate",
                    "POST",
                    "api/agent/investigate",
                    {
                        "title": "High error rate on account-service",
                        "description": "5xx spike in last 10 minutes",
                        "service": "account-service",
                        "category": "latency",
                        "severity": "WARNING",
                    },
                    base_var="opsAgentUrl",
                    auth=False,
                ),
                req("List Incidents", "GET", "api/agent/incidents", base_var="opsAgentUrl", auth=False),
                req("Get Incident", "GET", "api/agent/incidents/{{incidentId}}", base_var="opsAgentUrl", auth=False),
                req("Get Incident Report", "GET", "api/agent/incidents/{{incidentId}}/report", base_var="opsAgentUrl", auth=False),
                req(
                    "Approve Mitigation",
                    "POST",
                    "api/agent/incidents/{{incidentId}}/mitigations/{{actionId}}/approve",
                    base_var="opsAgentUrl",
                    auth=False,
                ),
                req(
                    "Alertmanager Webhook",
                    "POST",
                    "api/agent/webhooks/alertmanager",
                    {
                        "version": "4",
                        "status": "firing",
                        "alerts": [
                            {
                                "status": "firing",
                                "labels": {"alertname": "HighErrorRate", "service": "account-service"},
                                "annotations": {"summary": "Error rate above threshold"},
                            }
                        ],
                    },
                    base_var="opsAgentUrl",
                    auth=False,
                ),
                req(
                    "Chat",
                    "POST",
                    "api/agent/chat",
                    {"message": "Why is account-service returning 503?", "incidentId": "{{incidentId}}"},
                    base_var="opsAgentUrl",
                    auth=False,
                ),
            ],
            "Ops agent runs on port 8085 (not routed through API gateway).",
        ),
    ],
}

OUTPUT.write_text(json.dumps(collection, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {OUTPUT}")
