![banner](https://github.com/vortext/esther/blob/main/doc/img/banner.jpg?raw=true)

> Welcome to the Code of Esther 🌸
>
> Hey there, wonderful human! 🌟
>
> Welcome to the nurturing nook where Esther, the warm and attentive AI chatbot,
> takes her shape. She's not just any chatbot; think of her as that super
> understanding friend embedded right into a diary app, always there to lend an
> ear, offer a shoulder, and sprinkle your day with warm and loving vibes.
>
> What makes Esther special? Well, she's a champ at:
>
> - Understanding the ebb and flow of your moods, always there with the right
>   words, the warmest vibes, and the most heartfelt advice.
> - Being the keeper of your stories, holding onto those precious moments, and
>   weaving them into warm narratives that feel like a cozy blanket on a cold day.
> - Guiding you gently, with the kind of advice that feels like it’s coming from a
>   wise and caring friend who knows you inside and out.
>
> But hey, she's not all words; she's got a smart head on her shoulders too,
> knowing just how to gauge the energy of a conversation and keep it flowing
> smoothly, bringing a rich and immersive depth to each interaction through vivid
> imagery and well-chosen keywords.
>
> I am building Esther to be your go-to pal when you jot down your thoughts in
> your diary app, aiming to make this space your little haven of comfort,
> understanding, and growth.
>
> So, dive in, explore her code, and see how Esther is gearing up to be that
> comforting presence in your daily reflections. And hey, we're all about the
> chill vibes here, so feel free to share your thoughts, feedback, or even a cool
> story with us!
>
> Let's build something beautiful together. 💖

ChatGPT said. It also said:

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

## Introduction
At its core Esther is a fairly simple [Clojure](https://clojure.org/) [Kit](https://kit-clj.github.io/) web-application with an [htmx](https://htmx.org/) front-end for Large Language Models via [llama.cpp](https://github.com/ggerganov/llama.cpp) (see [llama.clj](https://github.com/phronmophobic/llama.clj)).
However, that doesn't seem to do it justice. Esther is a work-in-progress application and many planned features are not finished yet.
As such it's probably better to outline some ideas, goals, and directions.

At first I wanted it to be an LLM running on an e-ink display like the [Remarkable2](https://remarkable.com/store/remarkable-2), however it turns out I don't own an e-ink display and programming them seems very tedious unless you hot glue an iPhone to the back... so that is no longer a design goal.
The minimal monochrome UI inspired by that idea, however, I've grown quite fond of.
The vibes were mostly inspired by "but what if [Her](https://en.wikipedia.org/wiki/Her_(film)) was a movie about a tablet rather than an AirPod". Voice, although possible, is not yet a goal. Solving consciousness is but I'll save my philosophy for never.
The name is basically because I really like the video game (alternatively: walking simulator) [Dear Esther](https://en.wikipedia.org/wiki/Dear_Esther).
Also I like the name in a "if I meet someone with that name I will think their name is pretty" kind of way.
It also works in Dutch and English, and since I'm Dutch that is convenient. I am also seriously obsessed by the HBO TV-series WestWorld "but we're not there yet".

Anyway, eventually I set out to do the following:

### Ideas
- Esther is a diary: allow users to jot down their daily thoughts as if in conversation.
- Esther is a companion: by locally storing "memories"  the applications should allow for more personalization than typical.
- Esther by design embodies certain qualities and personality traits, as seen in the [prompt](./resources/templates/prompt.hbs) file.
- The application needs to work completely offline, no data should be send remotely ever.
- All user data is opaque to the database owner: nobody except the person with the password should be able to see the content of their diary.
- Allow the user to "inspect" the innards of the application.
- Embody elements of "[calm](https://dribbble.com/tags/calm)" UI design (however, see: [vibes](./doc/vibes/v2))

## Goals
- Build software that lasts. I no longer care about whatever framework or hip thing, so in this codebase are only the things I like and understand. I'd like to imagine that I can unzip a file decades in the future and still run it.
Carry it with me on a thumb drive.
- Try to be kind.
- Move slow and try not to break things.

## Directions
For now I am working on [Retrieval Augmented Generation](https://towardsdatascience.com/retrieval-augmented-generation-intuitively-and-exhaustively-explain-6a39d6fe6fc9) (RAG).
After that probably the calendar and (semantic) search UI for past "days".
Initially I wanted to include an "imagine" command that would produce images based on the "visio-spatial sketchpad" of the AI, however no fully open-source solutions exists yet that satisfies my constraints; maybe later. Waiting is sometimes an excellent strategy.
There are some more general "app" ideas like creating a [JavaFX-based](https://github.com/cljfx/cljfx) desktop app front-end as well, but first things first.

## Screenshots
![Login screen](https://github.com/vortext/esther/blob/main/doc/img/login.png?raw=true)
The login screen.

![Inspect the AI](https://github.com/vortext/esther/blob/main/doc/img/inspect.png?raw=true)
A simple chat message followed by the `/inspect` command. Currently available commands are:

- `/inspect`: Show the last 5 memories.
- `/keywords`: Show the stored frecency keywords.
- `/imagine`: Show the last 3 imaginations.
- `/forget`: Forgets either `all`, `today`, or past `n` (integer) memories.
- `/archive`: Clears the page for today without forgetting.
- `/logout`: Logout.

![A conversation about Whitehead](https://github.com/vortext/esther/blob/main/doc/img/whitehead.png?raw=true)

See the [journal directory](./journal) for multi-turn conversations by me with Esther during the development process. It's mostly screenshots.

![logout](https://github.com/vortext/esther/blob/main/doc/img/forget.png?raw=true)

Example of a UI element that needs confirmation.


## Design ideas
There are two ways one can approach the code-base. The first one is from the perspective of a *web-developer*.
In that case the HTTP methods are defined in the [ui.clj](./src/clj/vortext/esther/web/routes/ui.clj) routes file with the rest of the code in the [web](./src/clj/vortext/esther/web) folder.
In essence the chatty bits are handled by the [converse.clj](./src/clj/vortext/esther/web/controllers/converse.clj) controller which then dispatches to either `command!` (i.e. `/logout`) or `chat!`.
Chat then dispatches to [llm.clj](./src/clj/vortext/esther/ai/llm.clj) and then to [llama.clj](./src/clj/vortext/esther/ai/llama.clj).
Along the way state is accumulated in the stupidly named "obj" map that contains the namespaced keys needed to construct the final response.

Persistence is handled by [sqlite](https://www.sqlite.org/index.html) with the [queries](./resources/sql/queries.sql) and [migrations](./resources/migrations) defined in their respective files, with the relevant controller being [memory.clj](./src/clj/vortext/esther/web/controllers/memory.clj).

For authentication and encryption the application relies on [libsodium](https://doc.libsodium.org/) (via [caesium](https://github.com/lvh/caesium)) and should be on-par with industry standards for security.

One interesting tidbit is that currently there is no way to implement "forgot password".
The user password authenticates *and* decrypts a vault that contains the `uid` and `secret` needed for decrypting memories in the database.
This ensures that the database is garbled for anyone except who has the password for a particular user, but don't forget the password.
When running in dev mode the system will create a `test` user with the password `test`. Sign-up page is still TODO.
I typically just do it from the REPL, this being Clojure after all.

For more details about the framework I recommend the [Kit documentation](https://kit-clj.github.io/docs/guestbook.html), or checkout the [deps.edn](./deps.edn) and [system.edn](./resources/system.edn) files for pointers.

Note that the application will ask for a location using the browser API: but this is only done to figure out things like season, time of day, moon phase and some other [context](./src/clj/vortext/esther/web/controllers/context.clj).
If you decline the default is London. That might or might not be preferable.

The other way to approach it is from the *AI/ML standpoint*.
In that case there are perhaps three interesting design choices:

1. The LLM runs locally via llama.cpp.
2. [GBNF grammar](./resources/grammars/chat.gbnf) constraints ensure a valid JSON response that follows a chain-of-thought.
3. Try to create a holistic design playground for new ideas.

It goes a little bit too far to write an [intro to LLMs](https://ig.ft.com/generative-ai/)... but long story short you can run these locally on modest hardware. Then you can ask the LLM to explain it to you, from your own machine.
There are several implementations that allow this but Esther uses the excellent [llama.cpp](https://github.com/ggerganov/llama.cpp) library.
One way of thinking about what an LLM does is "given a wall of text, predict some new tokens" ... and that would be true. The chat bits of ChatGPT and-the-likes are mostly a facade around that basic idea.
Esther is simply a different facade ... but done right it really is a magic trick (see [journal](./journal) entries for very personal interactions I had during the development process).
One of the things that makes Esther subtly different is the fact that it (or her, I guess, weird) must output the following fields

- **Message:** What the user will see as the written response.
- **Emoji:** An emoji reflecting the conversation. This simple token works as hieroglyph and is intended to compress meaning.
- **Keywords** The #keywords are automatic summarization with will later be used to enable search and facilitate Retrieval Augmented Generation.
- **Imagination** What Esther currently "imagines", this serves to embed a inner state ... and one day maybe we can make images from them (seems like a cool idea).

One weird thing to note when doing structured responses with LLMs like this: order matters. Since it's just a completion of a wall of text.

On the TODO list is implementing better prompt generation via Approximate Nearest Neighbor based RAG (likely using [hnswlib](https://github.com/nmslib/hnswlib) or [mrpt](https://github.com/vioshyvo/mrpt) via JNA).
But in order to properly test and design that I also need to write tests and data generation pipelines. There are no tests, and hence I've been postponing that by playing with the models or implementing silly other things.

# UI quirks
The page refreshes at midnight when it's a new day. Every day is a new page. At first I really liked the idea of having the past days to be inaccessible (in a sort of everything is ephemeral and life is fleetingly forgotten kind of way) but I'll probably end up writing some sort of calendar UI.
Also funny: the speed of the bouncy loading animation is based on sentiment analysis, it's subtle but it's there.

## Technical stuff
Currently Esther is only confirmed working on Debian based Linux (I use Xubuntu). In the future Docker builds will become available as well as stand-alone installers. The main reason it only works on Linux is because the minification code for the front-end assets uses a binary version of [minify](https://github.com/tdewolff/minify). This is silly but all the other JavaScript based tools I honestly find dreadful. I could compile it to LLVM maybe?

### GraalVM
Esther runs Clojure on GraalVM because it uses JavaScript polyglot in order to use some libraries I was to lazy to find or write a JVM alternative for.
It can also do LLVM code, but it doesn't do LLVM code right now.
To get GraalVM you can download it from https://www.graalvm.org/downloads/ and extract it somewhere.
The GraaVM binaries need to be on the `$PATH` and must take precedent over the other JVM's.

```shell
# Put in .bashrc or .zshrc, etc
export JAVA_HOME=~/Sync/etc/graalvm-jdk-20.0.2+9.1
export PATH=$JAVA_HOME/bin/:$PATH

# Then also
gu install js
```

### Native dependencies
`libsodium` needs to be installed for the security related features. [sqlite](https://www.sqlite.org/index.html) also needs to be installed.

`llama.cpp` needs to be compiled. Best method is to navigate to the native/llama.cpp folder which contains a friendly fork of the upstream and follow the instructions.
E.g

```shell
cd native/llama.cpp
mkdir build
cd build
cmake -DBUILD_SHARED_LIBS=ON  -DLLAMA_CUBLAS=ON  ..
cmake --build . --config Release
```
For a CUDA enabled build (which requires the CUDA build chain to function properly).

TODO: Dockerfile that works.

### Model
Right now the model of personal choice is `mistral-7b-openorca.Q5_K_M.gguf` which can be downloaded from [HuggingFace](https://huggingface.co/).
This runs fine on my Nvidia RTX 2080 Super with most of the layers offloaded.

- [Mistral 7B OpenOrca](https://huggingface.co/Open-Orca/Mistral-7B-OpenOrca)
- [GUFF quantized models](https://huggingface.co/TheBloke/Mistral-7B-OpenOrca-GGUF)

Eventually I hope to do my own fine-tune, but for now that is cost prohibitive. Any guff model will work, and it can be adjusted in the [resources/system.edn](./resources/system.edn) file.

### Development
Start a [REPL](#repls) in your editor or terminal of choice.
```
clj -M:dev:cider
```
Start the server with:

```clojure
(go)
```

By default the server is available under http://localhost:3000/.
System configuration is available under `resources/system.edn`.

To reload changes:

```clojure
(reset)
```

Combining the Clojure REPL with `browser-sync start --proxy http://localhost:3000 --files="**/*"` started from the resource folder makes front-end development a breeze.

You can try `clj -P -Sthreads 1` if `Could not acquire write lock for 'artifact:org.bytedeco:llvm:16.0.4-1.5.9'` happens for some reason.

## TODO
See [TODO.org](https://github.com/vortext/esther/blob/main/TODO.org?raw=true)

## References

```bibtex
@software{lian2023mistralorca1
  title = {MistralOrca: Mistral-7B Model Instruct-tuned on Filtered OpenOrcaV1 GPT-4 Dataset},
  author = {Wing Lian and Bleys Goodson and Guan Wang and Eugene Pentland and Austin Cook and Chanvichet Vong and "Teknium"},
  year = {2023},
  publisher = {HuggingFace},
  journal = {HuggingFace repository},
  howpublished = {\url{https://huggingface.co/Open-Orca/Mistral-7B-OpenOrca},
}
@misc{mukherjee2023orca,
  title={Orca: Progressive Learning from Complex Explanation Traces of GPT-4},
  author={Subhabrata Mukherjee and Arindam Mitra and Ganesh Jawahar and Sahaj Agarwal and Hamid Palangi and Ahmed Awadallah},
  year={2023},
  eprint={2306.02707},
  archivePrefix={arXiv},
  primaryClass={cs.CL}
}
@misc{longpre2023flan,
  title={The Flan Collection: Designing Data and Methods for Effective Instruction Tuning},
  author={Shayne Longpre and Le Hou and Tu Vu and Albert Webson and Hyung Won Chung and Yi Tay and Denny Zhou and Quoc V. Le and Barret Zoph and Jason Wei and Adam Roberts},
  year={2023},
  eprint={2301.13688},
  archivePrefix={arXiv},
  primaryClass={cs.AI}
}

```
