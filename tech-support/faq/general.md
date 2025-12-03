# Frequently Asked Questions

## General Questions

### Q: What AI providers does ResearchAI support?
A: ResearchAI supports multiple AI providers:
- Claude (Anthropic) - Default provider
- OpenAI (GPT-4, GPT-3.5)
- HuggingFace (various models)
- Ollama (local models)

### Q: How do I change the AI model?
A: You can specify the model in several ways:
1. Set default in `.env` file: `CLAUDE_MODEL=claude-sonnet-4-5`
2. Pass in API request: `{"model": "claude-sonnet-4-5"}`
3. Select in web UI dropdown

### Q: What is RAG and how do I use it?
A: RAG (Retrieval-Augmented Generation) allows the AI to use your documents as context. To use:
1. Upload documents via `/rag/documents` endpoint or web UI
2. Enable RAG in chat settings
3. Ask questions - the system will search your documents for relevant context

### Q: How are chat sessions managed?
A: Chat sessions are automatically:
- Created on first message
- Persisted to disk (JSON format)
- Restored on server restart
- Compressed when too long (configurable strategies)

## API Questions

### Q: What is the API rate limit?
A: Default rate limits:
- 60 requests per minute for chat
- 100 requests per minute for RAG search
- Contact support for higher limits

### Q: How do I get my API key?
A: API keys can be obtained by:
1. Creating an account on the web interface
2. Navigating to Settings > API Keys
3. Generating a new key

### Q: Can I use multiple providers in one session?
A: Yes, you can switch providers within a session by specifying `providerId` in your request. The conversation history is maintained across provider switches.

## Troubleshooting

### Q: Why are my messages timing out?
A: Message timeouts usually occur due to:
- Long AI responses (increase timeout in client)
- Network issues (check connectivity)
- Server overload (try again later)
Default timeout is 5 minutes.

### Q: Why is the AI response cut off?
A: Response truncation can happen due to:
- `maxTokens` limit reached (increase limit)
- Context window exceeded (enable compression)
- Network interruption (retry request)

### Q: How do I reset a session?
A: To reset a session:
1. API: `POST /sessions/{sessionId}/clear`
2. Web UI: Click "New Chat" button
3. CLI: `rai chat --new`
