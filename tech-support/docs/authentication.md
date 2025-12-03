# Authentication Guide

## Overview

ResearchAI supports multiple authentication methods for securing API access.

## Authentication Methods

### 1. JWT Token Authentication

JWT (JSON Web Token) authentication is the recommended method for production use.

**Configuration:**
- Set `JWT_SECRET` environment variable
- Set `JWT_ISSUER` environment variable
- Set `JWT_AUDIENCE` environment variable

**Usage:**
Include the token in the Authorization header:
```
Authorization: Bearer <your-jwt-token>
```

### 2. Google OAuth Authentication

For web applications, Google OAuth provides user-friendly authentication.

**Configuration:**
- Set `GOOGLE_CLIENT_ID` environment variable
- Set `GOOGLE_CLIENT_SECRET` environment variable
- Configure `ALLOWED_EMAILS` for whitelist (comma-separated)

**Endpoints:**
- `GET /auth/google/login` - Initiates OAuth flow
- `GET /auth/google/callback` - OAuth callback handler
- `GET /auth/status` - Check authentication status

### 3. API Key Authentication

For simple integrations, API key authentication is available.

**Configuration:**
- Generate API key via admin panel
- Include in requests:
```
X-API-Key: <your-api-key>
```

## Common Issues

1. **Token Expired**: JWT tokens have configurable expiration. Refresh tokens before expiry.
2. **Invalid Signature**: Ensure JWT_SECRET matches between token generation and verification.
3. **OAuth Redirect Mismatch**: Verify redirect URI in Google Console matches your application.

## Troubleshooting

### "401 Unauthorized" Error
- Check if token is included in request
- Verify token has not expired
- Ensure correct authentication method is used

### "403 Forbidden" Error
- User may not be in whitelist
- Check ALLOWED_EMAILS configuration
- Verify user has required permissions
