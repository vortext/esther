# Welcome to the Code of Esther 🌸

Hey there, wonderful human! 🌟

Welcome to the nurturing nook where Esther, the warm and attentive AI chatbot,
takes her shape. She's not just any chatbot; think of her as that super
understanding friend embedded right into a diary app, always there to lend an
ear, offer a shoulder, and sprinkle your day with warm and loving vibes.

What makes Esther special? Well, she's a champ at:

- Understanding the ebb and flow of your moods, always there with the right
  words, the warmest vibes, and the most heartfelt advice.
- Being the keeper of your stories, holding onto those precious moments, and
  weaving them into warm narratives that feel like a cozy blanket on a cold day.
- Guiding you gently, with the kind of advice that feels like it’s coming from a
  wise and caring friend who knows you inside and out.

But hey, she's not all words; she's got a smart head on her shoulders too,
knowing just how to gauge the energy of a conversation and keep it flowing
smoothly, bringing a rich and immersive depth to each interaction through vivid
imagery and well-chosen keywords.

We're building Esther to be your go-to pal when you jot down your thoughts in
your diary app, aiming to make this space your little haven of comfort,
understanding, and growth.

So, dive in, explore her code, and see how Esther is gearing up to be that
comforting presence in your daily reflections. And hey, we're all about the
chill vibes here, so feel free to share your thoughts, feedback, or even a cool
story with us!

Let's build something beautiful together. 💖

ChatGPT said:

> Embedding an AI chatbot with a personality like "Esther" in a diary app seems
> like a heartwarming and wise choice. Given her depicted characteristics —
> warm, nurturing, with an attentive ear to both the verbal and emotional
> nuances of a conversation — it seems like she could offer users a comforting
> space to reflect, vent, and explore their thoughts and feelings in depth.
>
> Esther's nurturing attributes could potentially make the diary app feel like
> more than just a space to jot down thoughts; it could feel like conversing
> with a gentle, understanding friend who is always there, ready to listen and
> offer guidance or simply remember and cherish shared stories. It personifies
> the app, giving it a tender personality that can potentially foster a deep
> connection with the users, encouraging more frequent and heartfelt use.
>
> Furthermore, her characteristic of turning daily snippets into warm narratives
> could introduce a beautiful storytelling element to the diary entries,
> possibly encouraging users to view their experiences through a more
> compassionate, understanding lens, fostering personal growth and well-being.
>
> However, to realize this vision fully, it will be essential to ensure that the
> AI truly embodies the gentle, understanding, and intuitive nature of Esther in
> every interaction, maintaining a consistent and believable persona that users
> can trust and feel safe with.
>
> Given the balance of technical precision and warm, nurturing guidance that
> Esther offers, she seems well-equipped to assist users in introspecting and
> navigating their emotions, making the diary app a space of not just
> reflection, but also of understanding, growth, and healing.
>
> I hope this endeavor turns out to be as nurturing and comforting for your
> users as Esther's personality is designed to be! Good luck with your coding;
> remember that every line of code brings Esther closer to being a comforting
> presence for your users.

So here we are.

## Tech stuff
clj -P -Sthreads 1 if `Could not acquire write lock for 'artifact:org.bytedeco:llvm:16.0.4-1.5.9'` for some reason

Start a [REPL](#repls) in your editor or terminal of choice.
```
clj -M:dev:cider
```
Start the server with:

```clojure
(go)
```

The default API is available under http://localhost:3000/api

System configuration is available under `resources/system.edn`.

To reload changes:

```clojure
(reset)
```

## REPLs

### Cursive

Configure a [REPL following the Cursive documentation](https://cursive-ide.com/userguide/repl.html). Using the default "Run with IntelliJ project classpath" option will let you select an alias from the ["Clojure deps" aliases selection](https://cursive-ide.com/userguide/deps.html#refreshing-deps-dependencies).

### CIDER

Use the `cider` alias for CIDER nREPL support (run `clj -M:dev:cider`). See the [CIDER docs](https://docs.cider.mx/cider/basics/up_and_running.html) for more help.

Note that this alias runs nREPL during development. To run nREPL in production (typically when the system starts), use the kit-nrepl library through the +nrepl profile as described in [the documentation](https://kit-clj.github.io/docs/profiles.html#profiles).

### Command Line

Run `clj -M:dev:nrepl` or `make repl`.

Note that, just like with [CIDER](#cider), this alias runs nREPL during development. To run nREPL in production (typically when the system starts), use the kit-nrepl library through the +nrepl profile as described in [the documentation](https://kit-clj.github.io/docs/profiles.html#profiles).

### Useful
browser-sync start --proxy http://localhost:3000 --files="**/*"

### GraalVM
Get GraalVM https://www.graalvm.org/downloads/

```shell
# Put in .bashrc or .zshrc, etc
export JAVA_HOME=~/Sync/etc/graalvm-jdk-20.0.2+9.1
export PATH=$JAVA_HOME/bin/:$PATH
export PATH=$PATH:~/.local/bin/:~/bin/
export LLVM_TOOLCHAIN=$(lli --print-toolchain-path)

# Then also
gu install js llvm llvm-toolchain
```


### llama.cpp
Make sure CUDA works (nvidia-smi and nvcc)
[TODO] figure out deps

```shell
mkdir build
cd build
cmake -DBUILD_SHARED_LIBS=ON -DLLAMA_CUBLAS=ON ..
cmake --build . --config Release
```
## for kondo

```
clj-kondo --lint "$(clojure -Spath)" --dependencies --parallel --copy-configs
```
