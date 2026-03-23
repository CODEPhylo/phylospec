# On Error Messages

See https://elm-lang.org/news/compiler-errors-for-humans.

We have two main ways how errors can reach a user: through the LSP in a tooltip, or through a console output.

- The LSP needs the precise location of an error to highlight.
- The console output should also show the entire line to give more context.
- Every error message should consist of an error type, a description of what's wrong, and a hint on how to fix it.  

