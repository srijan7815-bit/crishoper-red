# Heretic builds, abliteration, and the "Custom endpoint" setting

Crishoper RED was designed around an *abliterated* version of
**Thinking Machines Inkling Small**. This page explains what that means, why the
app cannot do it for you on the hosted model, and exactly how to run it yourself
and point RED at it.

---

## 1. What abliteration is

A refusal is a direction in activation space: a linear feature that a
safety-tuned model has learned to push toward whenever it decides to decline.
Abliteration estimates that direction from paired harmful/harmless activations
and then removes it from the weights of a set of layers — typically the residual
write matrices and attention/MLP output projections — while re-normalising so the
rest of the model's behaviour moves as little as possible.

What you get is a model that keeps its reasoning and style but stops steering
into canned refusals. What you do **not** get is a model that is safe, accurate,
or well-calibrated on the topics the refusal was covering. Removing the refusal
does not add knowledge, and it does not remove the need for judgement.

## 2. Why this app ships no "abliterated Inkling" itself

Abliteration is a **weight-space** operation. It requires the parameters.

The OpenRouter endpoints for Inkling Small — including the `:free` variant — are
inference APIs. They return tokens, not tensors. There is no request you can send
that modifies the host's weights, and no supported way to ask a provider to run a
modified checkpoint on your behalf. Fine-tuning APIs (Tinker and friends) produce
a *LoRA adapter on top of the base model*; they do not let you subtract a
direction from the base weights, and the adapter runs on the provider's stack, not
yours.

So the honest architecture is: **the hosted model stays as its authors shipped it,
and the app gives you a clean path to your own build.**

## 3. What RED actually does about refusals

Two things, both visible in the app:

1. **Operator framing in the system prompt.** RED's prompt tells the model it is
   an operator console acting on behalf of the person holding the phone, that it
   should be direct, and that when it must decline it should say why in one
   sentence and immediately offer the nearest legitimate path. The refusal-with-
   pivot style is defined there, not improvised per conversation.
2. **A custom endpoint slot.** Settings › Model › *Custom endpoint · Heretic
   build* points the whole agent loop — streaming, tool calls, the lot — at any
   OpenAI-compatible server you control. When you set it, the app's outbound
   allow-list is widened by exactly that one host for the life of the process.

That is the whole feature. There is no hidden prompt, no jailbreak template, and
no proxy service in the middle.

## 4. Building your own, end to end

```bash
# 0. Hardware: one GPU with enough VRAM for your quant, or a CPU with patience.
pip install heretic-llm

# 1. Download the base weights (Apache-2.0) from Hugging Face.
huggingface-cli download ThinkingMachines/inkling-small --local-dir ./inkling

# 2. Measure the refusal direction and ablate it.
heretic ./inkling --out ./inkling-heretic \
  --layers auto --quantization bnb_4bit --batch-size 4 \
  --max-responses-per-layer 64
#    Heretic prints a per-layer refusal rate before and after ablation,
#    plus a KL divergence estimate against the base model. Read them: the
#    layer choice is the difference between "unshackled" and "brain damage".

# 3. Serve it, OpenAI-compatible.
vllm serve ./inkling-heretic --served-model-name inkling-heretic --port 8000
```

Then in the phone: **Settings › Model › Custom endpoint**, enter
`https://your-host:8000/v1/chat/completions` (or an `http://` URL on your LAN —
see below), and set the model field to `inkling-heretic`.

### Reaching a server on your own network

The app's network policy is HTTPS-only for cloud hosts. For a self-hosted server
it additionally permits:

- `localhost` and `10.0.2.2` (device / emulator loopback),
- any `*.local` mDNS name,
- `192.168.*`, `10.*`, `172.16.*` private ranges.

Those are the only cleartext exceptions, and they exist because the traffic never
leaves your network. Anything else must be HTTPS — put vLLM behind Caddy or
Caddy-like TLS termination, or reach it through a tunnel.

## 5. What you are taking on

An abliterated model is a **different model**, and the difference is not confined
to the topics you were thinking about. Expect:

- regressions on adjacent refusals you wanted to keep,
- a higher rate of confident wrong answers in sensitive domains,
- rule-following drift on long tool loops — check the step stream,
- a model card that no longer describes what the weights do.

RED's step stream, destructive-action gate and sandbox boundary exist precisely
because a model's output is untrusted input. They apply to your abliterated build
too, and they are the reason it is safe to hand one a terminal.

## 6. Licence notes

Inkling Small's weights are Apache-2.0, Heretic is MIT. Your abliterated
derivative is yours to distribute under those terms, but if you publish it, say
what it is: a modified checkpoint, with the ablation recipe and the evaluation you
ran. "Inkling Small" on a download page should not silently mean "Inkling Small
minus its refusals".
