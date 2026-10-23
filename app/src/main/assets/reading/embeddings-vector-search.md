# Embeddings & Vector Search: How Computers Learned to Vibe-Check Your Sentences

*A deep dive into turning words into arrows, why "king minus man plus woman" was the nerdiest party trick of the 2010s, what a "vector database" actually is once you strip the marketing off it, and why RAG needed all of this just to stop making things up.*

---

Let's start with the sentence that made an entire generation of computer scientists lose their minds a little: **you can take the vector for "king," subtract "man," add "woman," and land suspiciously close to "queen."**

Sit with that for a second. Nobody told the model that royalty and gender are related concepts. Nobody hand-coded a rule that says "monarchical status is orthogonal to biological sex, please adjust accordingly." The model just... found it, buried in the geometry, purely from reading a stupid amount of text and being forced to represent every word as a list of numbers. This article is about how that's possible, why it's the backbone of basically every "AI search" feature you've used this year, and why your chatbot needed to learn geometry before it could stop lying to you about your company's return policy.

## Step One: Turning Words Into Arrows, Which Is a Deeply Weird Thing to Do

Computers don't understand words. They understand numbers. So the very first move in almost any NLP pipeline is: take a word (or sentence, or document, or image, honestly at this point people embed everything including, unfortunately, entire personalities) and represent it as a list of numbers — a **vector**.

```
"king"  -> [0.21, -0.45, 0.83, ..., 0.12]   (maybe 300, maybe 1536, maybe 4096 numbers long)
```

This is called an **embedding**. The name is doing real work — you're "embedding" a word into a high-dimensional space, the same way you might embed a photo into a picture frame, except the frame has, depending on the model, anywhere from a few hundred to several thousand dimensions, which is a phrase that should feel more alarming than it usually does when people say it casually in meetings.

The magic isn't the numbers themselves — a random list of 300 floats is meaningless. The magic is that **similar things end up near each other**. "King" and "queen" end up close together in this space. "King" and "banana" do not, unless something has gone very wrong with your training data. The entire point of an embedding is that *distance in this bizarre numerical space corresponds to meaning* — which is either one of the most elegant ideas in modern computing or a slightly unsettling reminder that meaning itself might just be geometry wearing a trench coat.

## How a Model Learns Where to Put the Arrows

Early embedding techniques — Word2Vec, GloVe, the classics your professor made you implement once and never again — learned this by a beautifully simple trick: **words that show up in similar contexts get similar vectors.** "Coffee" and "tea" both show up near "drink," "morning," "cup," and "caffeine" constantly across a giant corpus of text, so the training process nudges their vectors toward each other, even though the model has literally never seen a definition of either word. It's not being told what coffee is. It's inferring "these two words keep showing up in the same neighborhoods, they're probably neighbors," which is basically how you figure out someone's a regular at your local café — not because they introduced themselves, but because they're always there at 8 AM ordering the same thing.

Modern embeddings — the ones coming out of Transformer-based models like BERT (hello again, old friend from a previous article) — do something even richer: the vector for a word can shift depending on its context in the sentence. "Bank" in "river bank" and "bank" in "bank account" get genuinely different embeddings, because the model isn't just memorizing a fixed lookup table anymore, it's actually reading the sentence around the word before deciding where to place it. Word2Vec gave every word one fixed address for life. Contextual embeddings give a word a different address depending on who it showed up with that day, which is a much more accurate model of how language — and frankly, most people at parties — actually behaves.

## Cosine Similarity: How You Measure "Close" When "Close" Means Something Weird

Okay, so you've got two vectors. How do you actually check if they're "similar"? The intuitive answer — just measure the straight-line distance between them, like you would on a map — turns out to be the wrong tool for this specific job, and the reason why is genuinely worth understanding instead of just memorizing.

**Cosine similarity** doesn't measure the distance between two vectors. It measures the **angle** between them.

```
cosine_similarity(A, B) = (A · B) / (||A|| × ||B||)
```

Translated out of math-speak: take the dot product of the two vectors, divide by the product of their magnitudes (their lengths), and you get a number between -1 and 1. A cosine similarity of 1 means the vectors point in exactly the same direction — maximally similar. 0 means they're perpendicular, completely unrelated. -1 means they point in opposite directions, which in embedding-land often means something closer to "opposite concept" than "unrelated."

Here's why angle beats raw distance for this specific job: **two vectors can point in the exact same direction and still have wildly different magnitudes**, and that magnitude difference is usually noise you don't care about — a longer document just naturally accumulates a "bigger" vector than a short one, purely from having more words contribute to it, not because it's more "intense" about the topic. Raw Euclidean distance would penalize the longer document for being long. Cosine similarity looks past that entirely and asks only "are these two things pointing the same way," which is almost always the actual question you meant to ask. It's the difference between measuring how far apart two people are standing versus checking whether they're facing the same direction — and when you're trying to figure out if two sentences mean the same thing, direction is the question that actually matters, not how loudly each one happens to be saying it.

## Okay, So Now You Have a Billion Vectors. Now What.

Here's where the practical problem starts. You've embedded every product in your catalog, every support article, every paragraph of your company's documentation — you now have, let's say, ten million vectors, each one 1,536 numbers long. A user types a search query, you embed *that* too, and now you need to find which of your ten million stored vectors is most similar to the query vector.

The naive approach: compute cosine similarity between the query and every single one of your ten million vectors, one at a time, and sort. This is called **brute-force search**, and it works completely fine on a laptop with a thousand vectors and becomes an actual catastrophe at real scale — comparing against ten million high-dimensional vectors for every single search, every single time, is the computational equivalent of losing your car keys and deciding the correct strategy is to personally interview every object in your house.

This is the entire reason **vector databases** exist. Strip away the marketing copy on basically any vector database's homepage and what you're actually buying is: a system that's specifically, aggressively optimized to answer "find me the nearest neighbors to this vector" fast, at scale, without doing a brute-force sweep of your entire dataset every time. It's not a mystical new category of database that thinks in higher dimensions and vibes its way to an answer — it's an indexing problem, the same genre of problem as "how do I look up a word in a dictionary without reading every page," just in a few thousand dimensions instead of alphabetical order, which turns out to be considerably harder than alphabetical order once you actually try it.

## HNSW: The Actual Trick Behind Most Vector Databases

The specific technique doing most of the heavy lifting in modern vector databases (Pinecone, Weaviate, Milvus, pgvector, and friends) is usually some flavor of **Approximate Nearest Neighbor (ANN)** search, and the most popular algorithm behind it is called **HNSW — Hierarchical Navigable Small World** graphs, a name that sounds like it was generated by a fantasy-novel title generator that got fed a computer science textbook by accident.

The core idea, without the math: build a multi-layered graph where vectors are connected to their approximate neighbors, with **sparser, longer-range connections at the top layers and denser, short-range connections at the bottom**. Searching starts at the top layer, takes big greedy jumps toward the general neighborhood of your query, then drops down a layer and refines, then drops down again and refines further, narrowing in like a zoom lens instead of scanning every single point in the entire space.

It's less "check every possibility" and considerably more "ask a local who clearly knows the area" — start at the highway, get off at the right exit, then ask increasingly specific directions once you're actually in the right neighborhood, instead of walking door to door across an entire city checking every single address against your destination. It's **approximate**, not exact — HNSW might occasionally miss the single mathematically closest vector in exchange for being dramatically faster than an exhaustive search — and for the vast majority of real use cases, "the 5th closest match instead of the exact closest" is a completely imperceptible tradeoff against "search returns in 20 milliseconds instead of 8 seconds."

This tradeoff — accuracy for speed — is the entire personality of vector databases as a category. Nobody's promising you the mathematically perfect answer. They're promising you a very, very good answer, extremely fast, at a scale where "perfect" was never actually on the table to begin with.

## Why RAG Needed All of This

Now the payoff — why any of this matters to the chatbot you've actually used. **Retrieval-Augmented Generation (RAG)** exists to solve a very specific, very embarrassing problem: a language model's knowledge is frozen at training time, it has no access to your private documents, and when it doesn't know something, it doesn't reliably say "I don't know" — it confidently generates something that sounds correct and might be complete fiction. This is hallucination, and it's not a bug exactly, it's the model doing exactly what it was trained to do — predict plausible next tokens — in a situation where "plausible" and "true" have quietly come apart.

RAG's fix: **before** asking the language model to answer, go retrieve the actual relevant information first, and hand it to the model as context. And retrieval, it turns out, is exactly the "find the nearest neighbors to this query" problem we just spent this whole article on.

The actual pipeline, end to end:

1. Take your documents (support articles, internal wiki, PDFs, whatever) and chop them into chunks.
2. Embed every chunk into a vector, store it in a vector database.
3. When a user asks a question, embed *their question* using the same embedding model.
4. Use the vector database's ANN search (HNSW, most likely) to find the chunks whose embeddings are closest — via cosine similarity — to the question's embedding.
5. Stuff those retrieved chunks into the language model's prompt as context: "Here's what you know, here's what we found that's relevant, now answer using this."
6. The model generates an answer **grounded in the retrieved text**, instead of purely improvising from whatever it half-remembers from training.

This is why a customer support bot built on RAG can accurately answer "what's your refund policy for orders placed in the last 30 days" even though that exact sentence never appeared anywhere in the model's training data and possibly didn't even exist as a policy until last Tuesday. The model isn't remembering it. It's being handed the actual policy document, freshly retrieved, moments before answering — the AI equivalent of a smart friend who doesn't know the answer off the top of their head but is extremely good at finding the right page in the manual before confidently telling you what it says, instead of just guessing and hoping nobody checks.

Without embeddings and vector search, step 4 is impossible at any real scale — you can't hand a language model your entire company wiki on every single question, both because context windows have limits and because most of that wiki is irrelevant noise for any given question. Embeddings are what let you find the needle. Vector search is what lets you find it in under a second, out of a haystack with ten million other needles in it, all pointing in slightly different directions, waiting for someone to ask the right question.

## The Verdict

Embeddings are the quiet, unglamorous piece of infrastructure sitting underneath nearly everything people call "AI search" right now — turning meaning into geometry, close-in-meaning into close-in-space, and letting an entire downstream industry of vector databases exist purely to answer one narrow, extremely well-optimized question: given this point, what else is nearby. Cosine similarity is the ruler doing the measuring, deliberately blind to magnitude so it only cares about direction. HNSW is the shortcut that makes asking that question fast enough to matter at real scale, trading a sliver of precision for an amount of speed that makes the tradeoff not even worth arguing about. And RAG is the payoff — the reason any of this needed to exist outside a research paper — bolting a search engine onto a language model so it can stop confidently making things up and start confidently citing the actual document instead.

"King minus man plus woman equals queen" was always the party trick. Billions of dollars of production infrastructure quietly built on top of the same idea, so that a chatbot can correctly tell you your return window closes in nine days, is the part nobody puts in the demo.

---

*Next in this series: T5 and encoder-decoder models — the "why not both" answer to the BERT vs. GPT sibling rivalry, and why "everything is text-to-text" turned out to be an unreasonably good idea.*
