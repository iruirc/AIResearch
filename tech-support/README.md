# Tech Support RAG Content

This directory contains documentation for the Tech Support RAG knowledge base.

## Directory Structure

```
tech-support/
├── docs/           # Product documentation
│   └── *.md       # Technical guides, API docs, configuration
├── faq/           # Frequently asked questions
│   └── *.md       # Q&A format documents
├── known-issues/  # Known issues and workarounds
│   └── *.md       # Issue descriptions and resolutions
└── README.md      # This file
```

## Adding Content

### Documentation (docs/)
Add technical documentation in Markdown format:
- API guides
- Configuration instructions
- Architecture documentation
- Integration guides

### FAQ (faq/)
Add FAQ documents in Q&A format:
- Use `### Q:` for questions
- Use `A:` for answers
- Group related questions in single files

### Known Issues (known-issues/)
Document known issues with:
- Issue ID
- Status (Open, Under Investigation, Fixed)
- Severity (High, Medium, Low)
- Description and symptoms
- Workaround if available
- Fix ETA or resolution

## Indexing Content

To index this content into RAG:

```bash
# Using CLI
rai init --rag-path tech-support

# Using API
POST /rag/documents
{
  "name": "tech-support-docs",
  "sourceFiles": [
    {"fileName": "authentication.md", "content": "..."}
  ]
}
```

## Best Practices

1. **Keep content focused** - Each file should cover one topic
2. **Use clear headings** - Helps with chunking and retrieval
3. **Include keywords** - Add relevant terms for better search
4. **Update regularly** - Keep known issues and FAQ current
5. **Version control** - Track changes to documentation
