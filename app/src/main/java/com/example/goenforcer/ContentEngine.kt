package com.example.goenforcer

import android.content.Context

// ─── Data Models ───────────────────────────────────────────────────────
data class Lesson(
    val id: String,
    val title: String,
    val description: String,
    val topic: Topic,
    val snippets: List<SnippetContent>
)

data class SnippetContent(
    val id: String,
    val title: String,
    val rawCode: String, // Packed format: Kcode... SEP Ccomment...
    val difficulty: Difficulty = Difficulty.Medium
)

enum class Topic {
    Basics, Concurrency, Advanced
}

enum class Difficulty {
    Easy, Medium, Hard
}

// ─── Content Repository ────────────────────────────────────────────────
object ContentStore {
    private const val SEP = '\u001f'

    val LESSONS = listOf(
        Lesson(
            id = "basics_1",
            title = "The Go Gopher",
            description = "Welcome to the world of Go. Let's start with the basics.",
            topic = Topic.Basics,
            snippets = listOf(
                SnippetContent(
                    id = "hello",
                    title = "Hello, Gopher",
                    rawCode = "Kpackage main\n\nfunc main() {\n\u001fC// print a friendly message\n\u001fK    fmt.Println(\"Hello, Go Enforcer!\")\n}",
                    difficulty = Difficulty.Easy
                ),
                SnippetContent(
                    id = "vars",
                    title = "Variables",
                    rawCode = "Kpackage main\n\nfunc main() {\n    var message string = \"Go is fast\"\n    count := 42\n\u001fC// shorthand declaration\n\u001fK    fmt.Println(message, count)\n}",
                    difficulty = Difficulty.Easy
                )
            )
        ),
        Lesson(
            id = "goroutines_1",
            title = "Goroutines",
            description = "Start your first concurrent task with the 'go' keyword.",
            topic = Topic.Concurrency,
            snippets = listOf(
                SnippetContent(
                    id = "go_say",
                    title = "Independent talkers",
                    rawCode = "Kfunc main() {\n    go say(1, \"go is awesome\")\n    go say(2, \"cats are cute\")\n\u001fC// wait for a bit\n\u001fK    time.Sleep(500 * time.Millisecond)\n}",
                    difficulty = Difficulty.Medium
                ),
                SnippetContent(
                    id = "wg_sync",
                    title = "Wait Groups",
                    rawCode = "Kfunc main() {\n    var wg sync.WaitGroup\n    wg.Add(2)\n    go func() {\n        defer wg.Done()\n        say(\"Done 1\")\n    }()\n    wg.Wait()\n}",
                    difficulty = Difficulty.Medium
                )
            )
        ),
        Lesson(
            id = "channels_1",
            title = "Channels",
            description = "Synchronize goroutines and pass data safely.",
            topic = Topic.Concurrency,
            snippets = listOf(
                SnippetContent(
                    id = "chan_ping",
                    title = "Ping-Pong",
                    rawCode = "Kmessages := make(chan string)\ngo func() { messages <- \"ping\" }()\nmsg := <-messages\nfmt.Println(msg)",
                    difficulty = Difficulty.Medium
                ),
                SnippetContent(
                    id = "chan_close",
                    title = "Closing a channel",
                    rawCode = "Kgo func() {\n    for _, word := range words {\n        in <- word\n    }\n    close(in)\n}()",
                    difficulty = Difficulty.Medium
                )
            )
        ),
        Lesson(
            id = "context_1",
            title = "Context & Timeout",
            description = "Handle cancellation and deadlines in your pipelines.",
            topic = Topic.Advanced,
            snippets = listOf(
                SnippetContent(
                    id = "ctx_timeout",
                    title = "WithTimeout",
                    rawCode = "Kctx, cancel := context.WithTimeout(ctx, 100*ms)\ndefer cancel()\nres, err := execute(ctx, work)",
                    difficulty = Difficulty.Hard
                ),
                SnippetContent(
                    id = "ctx_afterfunc",
                    title = "AfterFunc",
                    rawCode = "Kcontext.AfterFunc(ctx, func() {\n    fmt.Println(\"cleanup\")\n})",
                    difficulty = Difficulty.Hard
                )
            )
        )
    )

    fun getAllSnippets(): List<SnippetContent> = LESSONS.flatMap { it.snippets }

    fun getDailySnippet(context: Context): SnippetContent {
        val idx = Store.resolveTodayIndex(context)
        val raw = GeneratedQuestionBank.get(idx)
        return SnippetContent(
            id = "gen_$idx",
            title = Core.questionTitle(raw),
            rawCode = raw
        )
    }
    
    fun getLessonsByTopic(topic: Topic): List<Lesson> = LESSONS.filter { it.topic == topic }
}
