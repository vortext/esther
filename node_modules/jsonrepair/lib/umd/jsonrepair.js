(function (global, factory) {
  typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports) :
  typeof define === 'function' && define.amd ? define(['exports'], factory) :
  (global = typeof globalThis !== 'undefined' ? globalThis : global || self, factory(global.JSONRepair = {}));
})(this, (function (exports) { 'use strict';

  class JSONRepairError extends Error {
    constructor(message, position) {
      super(message + ' at position ' + position);
      this.position = position;
    }
  }

  // TODO: sort the codes
  const codeBackslash = 0x5c; // "\"
  const codeSlash = 0x2f; // "/"
  const codeAsterisk = 0x2a; // "*"
  const codeOpeningBrace = 0x7b; // "{"
  const codeClosingBrace = 0x7d; // "}"
  const codeOpeningBracket = 0x5b; // "["
  const codeClosingBracket = 0x5d; // "]"
  const codeOpenParenthesis = 0x28; // "("
  const codeCloseParenthesis = 0x29; // ")"
  const codeSpace = 0x20; // " "
  const codeNewline = 0xa; // "\n"
  const codeTab = 0x9; // "\t"
  const codeReturn = 0xd; // "\r"
  const codeBackspace = 0x08; // "\b"
  const codeFormFeed = 0x0c; // "\f"
  const codeDoubleQuote = 0x0022; // "
  const codePlus = 0x2b; // "+"
  const codeMinus = 0x2d; // "-"
  const codeQuote = 0x27; // "'"
  const codeZero = 0x30;
  const codeOne = 0x31;
  const codeNine = 0x39;
  const codeComma = 0x2c; // ","
  const codeDot = 0x2e; // "." (dot, period)
  const codeColon = 0x3a; // ":"
  const codeSemicolon = 0x3b; // ";"
  const codeUppercaseA = 0x41; // "A"
  const codeLowercaseA = 0x61; // "a"
  const codeUppercaseE = 0x45; // "E"
  const codeLowercaseE = 0x65; // "e"
  const codeUppercaseF = 0x46; // "F"
  const codeLowercaseF = 0x66; // "f"
  const codeNonBreakingSpace = 0xa0;
  const codeEnQuad = 0x2000;
  const codeHairSpace = 0x200a;
  const codeNarrowNoBreakSpace = 0x202f;
  const codeMediumMathematicalSpace = 0x205f;
  const codeIdeographicSpace = 0x3000;
  const codeDoubleQuoteLeft = 0x201c; // “
  const codeDoubleQuoteRight = 0x201d; // ”
  const codeQuoteLeft = 0x2018; // ‘
  const codeQuoteRight = 0x2019; // ’
  const codeGraveAccent = 0x0060; // `
  const codeAcuteAccent = 0x00b4; // ´

  function isHex(code) {
    return code >= codeZero && code <= codeNine || code >= codeUppercaseA && code <= codeUppercaseF || code >= codeLowercaseA && code <= codeLowercaseF;
  }
  function isDigit(code) {
    return code >= codeZero && code <= codeNine;
  }
  function isNonZeroDigit(code) {
    return code >= codeOne && code <= codeNine;
  }
  function isValidStringCharacter(code) {
    return code >= 0x20 && code <= 0x10ffff;
  }
  function isDelimiter(char) {
    return regexDelimiter.test(char) || char && isQuote(char.charCodeAt(0));
  }
  const regexDelimiter = /^[,:[\]{}()\n]$/;
  function isStartOfValue(char) {
    return regexStartOfValue.test(char) || char && isQuote(char.charCodeAt(0));
  }

  // alpha, number, minus, or opening bracket or brace
  const regexStartOfValue = /^[[{\w-]$/;
  function isControlCharacter(code) {
    return code === codeNewline || code === codeReturn || code === codeTab || code === codeBackspace || code === codeFormFeed;
  }

  /**
   * Check if the given character is a whitespace character like space, tab, or
   * newline
   */
  function isWhitespace(code) {
    return code === codeSpace || code === codeNewline || code === codeTab || code === codeReturn;
  }

  /**
   * Check if the given character is a special whitespace character, some
   * unicode variant
   */
  function isSpecialWhitespace(code) {
    return code === codeNonBreakingSpace || code >= codeEnQuad && code <= codeHairSpace || code === codeNarrowNoBreakSpace || code === codeMediumMathematicalSpace || code === codeIdeographicSpace;
  }

  /**
   * Test whether the given character is a quote or double quote character.
   * Also tests for special variants of quotes.
   */
  function isQuote(code) {
    // the first check double quotes, since that occurs most often
    return isDoubleQuoteLike(code) || isSingleQuoteLike(code);
  }

  /**
   * Test whether the given character is a double quote character.
   * Also tests for special variants of double quotes.
   */
  function isDoubleQuoteLike(code) {
    // the first check double quotes, since that occurs most often
    return code === codeDoubleQuote || code === codeDoubleQuoteLeft || code === codeDoubleQuoteRight;
  }

  /**
   * Test whether the given character is a double quote character.
   * Does NOT test for special variants of double quotes.
   */
  function isDoubleQuote(code) {
    return code === codeDoubleQuote;
  }

  /**
   * Test whether the given character is a single quote character.
   * Also tests for special variants of single quotes.
   */
  function isSingleQuoteLike(code) {
    return code === codeQuote || code === codeQuoteLeft || code === codeQuoteRight || code === codeGraveAccent || code === codeAcuteAccent;
  }

  /**
   * Strip last occurrence of textToStrip from text
   */
  function stripLastOccurrence(text, textToStrip) {
    let stripRemainingText = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : false;
    const index = text.lastIndexOf(textToStrip);
    return index !== -1 ? text.substring(0, index) + (stripRemainingText ? '' : text.substring(index + 1)) : text;
  }
  function insertBeforeLastWhitespace(text, textToInsert) {
    let index = text.length;
    if (!isWhitespace(text.charCodeAt(index - 1))) {
      // no trailing whitespaces
      return text + textToInsert;
    }
    while (isWhitespace(text.charCodeAt(index - 1))) {
      index--;
    }
    return text.substring(0, index) + textToInsert + text.substring(index);
  }
  function removeAtIndex(text, start, count) {
    return text.substring(0, start) + text.substring(start + count);
  }

  /**
   * Test whether a string ends with a newline or comma character and optional whitespace
   */
  function endsWithCommaOrNewline(text) {
    return /[,\n][ \t\r]*$/.test(text);
  }

  const controlCharacters = {
    '\b': '\\b',
    '\f': '\\f',
    '\n': '\\n',
    '\r': '\\r',
    '\t': '\\t'
  };

  // map with all escape characters
  const escapeCharacters = {
    '"': '"',
    '\\': '\\',
    '/': '/',
    b: '\b',
    f: '\f',
    n: '\n',
    r: '\r',
    t: '\t'
    // note that \u is handled separately in parseString()
  };

  /**
   * Repair a string containing an invalid JSON document.
   * For example changes JavaScript notation into JSON notation.
   *
   * Example:
   *
   *     try {
   *       const json = "{name: 'John'}"
   *       const repaired = jsonrepair(json)
   *       console.log(repaired)
   *       // '{"name": "John"}'
   *     } catch (err) {
   *       console.error(err)
   *     }
   *
   */
  function jsonrepair(text) {
    let i = 0; // current index in text
    let output = ''; // generated output

    const processed = parseValue();
    if (!processed) {
      throwUnexpectedEnd();
    }
    const processedComma = parseCharacter(codeComma);
    if (processedComma) {
      parseWhitespaceAndSkipComments();
    }
    if (isStartOfValue(text[i]) && endsWithCommaOrNewline(output)) {
      // start of a new value after end of the root level object: looks like
      // newline delimited JSON -> turn into a root level array
      if (!processedComma) {
        // repair missing comma
        output = insertBeforeLastWhitespace(output, ',');
      }
      parseNewlineDelimitedJSON();
    } else if (processedComma) {
      // repair: remove trailing comma
      output = stripLastOccurrence(output, ',');
    }
    if (i >= text.length) {
      // reached the end of the document properly
      return output;
    }
    throwUnexpectedCharacter();
    function parseValue() {
      parseWhitespaceAndSkipComments();
      const processed = parseObject() || parseArray() || parseString() || parseNumber() || parseKeywords() || parseUnquotedString();
      parseWhitespaceAndSkipComments();
      return processed;
    }
    function parseWhitespaceAndSkipComments() {
      const start = i;
      let changed = parseWhitespace();
      do {
        changed = parseComment();
        if (changed) {
          changed = parseWhitespace();
        }
      } while (changed);
      return i > start;
    }
    function parseWhitespace() {
      let whitespace = '';
      let normal;
      while ((normal = isWhitespace(text.charCodeAt(i))) || isSpecialWhitespace(text.charCodeAt(i))) {
        if (normal) {
          whitespace += text[i];
        } else {
          // repair special whitespace
          whitespace += ' ';
        }
        i++;
      }
      if (whitespace.length > 0) {
        output += whitespace;
        return true;
      }
      return false;
    }
    function parseComment() {
      // find a block comment '/* ... */'
      if (text.charCodeAt(i) === codeSlash && text.charCodeAt(i + 1) === codeAsterisk) {
        // repair block comment by skipping it
        while (i < text.length && !atEndOfBlockComment(text, i)) {
          i++;
        }
        i += 2;
        return true;
      }

      // find a line comment '// ...'
      if (text.charCodeAt(i) === codeSlash && text.charCodeAt(i + 1) === codeSlash) {
        // repair line comment by skipping it
        while (i < text.length && text.charCodeAt(i) !== codeNewline) {
          i++;
        }
        return true;
      }
      return false;
    }
    function parseCharacter(code) {
      if (text.charCodeAt(i) === code) {
        output += text[i];
        i++;
        return true;
      }
      return false;
    }
    function skipCharacter(code) {
      if (text.charCodeAt(i) === code) {
        i++;
        return true;
      }
      return false;
    }
    function skipEscapeCharacter() {
      return skipCharacter(codeBackslash);
    }

    /**
     * Parse an object like '{"key": "value"}'
     */
    function parseObject() {
      if (text.charCodeAt(i) === codeOpeningBrace) {
        output += '{';
        i++;
        parseWhitespaceAndSkipComments();
        let initial = true;
        while (i < text.length && text.charCodeAt(i) !== codeClosingBrace) {
          let processedComma;
          if (!initial) {
            processedComma = parseCharacter(codeComma);
            if (!processedComma) {
              // repair missing comma
              output = insertBeforeLastWhitespace(output, ',');
            }
            parseWhitespaceAndSkipComments();
          } else {
            processedComma = true;
            initial = false;
          }
          const processedKey = parseString() || parseUnquotedString();
          if (!processedKey) {
            if (text.charCodeAt(i) === codeClosingBrace || text.charCodeAt(i) === codeOpeningBrace || text.charCodeAt(i) === codeClosingBracket || text.charCodeAt(i) === codeOpeningBracket || text[i] === undefined) {
              // repair trailing comma
              output = stripLastOccurrence(output, ',');
            } else {
              throwObjectKeyExpected();
            }
            break;
          }
          parseWhitespaceAndSkipComments();
          const processedColon = parseCharacter(codeColon);
          if (!processedColon) {
            if (isStartOfValue(text[i])) {
              // repair missing colon
              output = insertBeforeLastWhitespace(output, ':');
            } else {
              throwColonExpected();
            }
          }
          const processedValue = parseValue();
          if (!processedValue) {
            if (processedColon) {
              // repair missing object value
              output += 'null';
            } else {
              throwColonExpected();
            }
          }
        }
        if (text.charCodeAt(i) === codeClosingBrace) {
          output += '}';
          i++;
        } else {
          // repair missing end bracket
          output = insertBeforeLastWhitespace(output, '}');
        }
        return true;
      }
      return false;
    }

    /**
     * Parse an array like '["item1", "item2", ...]'
     */
    function parseArray() {
      if (text.charCodeAt(i) === codeOpeningBracket) {
        output += '[';
        i++;
        parseWhitespaceAndSkipComments();
        let initial = true;
        while (i < text.length && text.charCodeAt(i) !== codeClosingBracket) {
          if (!initial) {
            const processedComma = parseCharacter(codeComma);
            if (!processedComma) {
              // repair missing comma
              output = insertBeforeLastWhitespace(output, ',');
            }
          } else {
            initial = false;
          }
          const processedValue = parseValue();
          if (!processedValue) {
            // repair trailing comma
            output = stripLastOccurrence(output, ',');
            break;
          }
        }
        if (text.charCodeAt(i) === codeClosingBracket) {
          output += ']';
          i++;
        } else {
          // repair missing closing array bracket
          output = insertBeforeLastWhitespace(output, ']');
        }
        return true;
      }
      return false;
    }

    /**
     * Parse and repair Newline Delimited JSON (NDJSON):
     * multiple JSON objects separated by a newline character
     */
    function parseNewlineDelimitedJSON() {
      // repair NDJSON
      let initial = true;
      let processedValue = true;
      while (processedValue) {
        if (!initial) {
          // parse optional comma, insert when missing
          const processedComma = parseCharacter(codeComma);
          if (!processedComma) {
            // repair: add missing comma
            output = insertBeforeLastWhitespace(output, ',');
          }
        } else {
          initial = false;
        }
        processedValue = parseValue();
      }
      if (!processedValue) {
        // repair: remove trailing comma
        output = stripLastOccurrence(output, ',');
      }

      // repair: wrap the output inside array brackets
      output = "[\n".concat(output, "\n]");
    }

    /**
     * Parse a string enclosed by double quotes "...". Can contain escaped quotes
     * Repair strings enclosed in single quotes or special quotes
     * Repair an escaped string
     */
    function parseString() {
      let skipEscapeChars = text.charCodeAt(i) === codeBackslash;
      if (skipEscapeChars) {
        // repair: remove the first escape character
        i++;
        skipEscapeChars = true;
      }
      if (isQuote(text.charCodeAt(i))) {
        const isEndQuote = isSingleQuoteLike(text.charCodeAt(i)) ? isSingleQuoteLike : isDoubleQuote(text.charCodeAt(i)) ? isDoubleQuote // eslint-disable-line indent
        : isDoubleQuoteLike; // eslint-disable-line indent

        output += '"';
        i++;
        while (i < text.length && !isEndQuote(text.charCodeAt(i))) {
          if (text.charCodeAt(i) === codeBackslash) {
            const char = text[i + 1];
            const escapeChar = escapeCharacters[char];
            if (escapeChar !== undefined) {
              output += text.slice(i, i + 2);
              i += 2;
            } else if (char === 'u') {
              if (isHex(text.charCodeAt(i + 2)) && isHex(text.charCodeAt(i + 3)) && isHex(text.charCodeAt(i + 4)) && isHex(text.charCodeAt(i + 5))) {
                output += text.slice(i, i + 6);
                i += 6;
              } else {
                throwInvalidUnicodeCharacter(i);
              }
            } else {
              // repair invalid escape character: remove it
              output += char;
              i += 2;
            }
          } else {
            const char = text[i];
            const code = text.charCodeAt(i);
            if (code === codeDoubleQuote && text.charCodeAt(i - 1) !== codeBackslash) {
              // repair unescaped double quote
              output += '\\' + char;
              i++;
            } else if (isControlCharacter(code)) {
              // unescaped control character
              output += controlCharacters[char];
              i++;
            } else {
              if (!isValidStringCharacter(code)) {
                throwInvalidCharacter(char);
              }
              output += char;
              i++;
            }
          }
          if (skipEscapeChars) {
            skipEscapeCharacter();
          }
        }
        if (isQuote(text.charCodeAt(i))) {
          if (text.charCodeAt(i) !== codeDoubleQuote) ;
          output += '"';
          i++;
        } else {
          // repair missing end quote
          output += '"';
        }
        parseConcatenatedString();
        return true;
      }
      return false;
    }

    /**
     * Repair concatenated strings like "hello" + "world", change this into "helloworld"
     */
    function parseConcatenatedString() {
      let processed = false;
      parseWhitespaceAndSkipComments();
      while (text.charCodeAt(i) === codePlus) {
        processed = true;
        i++;
        parseWhitespaceAndSkipComments();

        // repair: remove the end quote of the first string
        output = stripLastOccurrence(output, '"', true);
        const start = output.length;
        parseString();

        // repair: remove the start quote of the second string
        output = removeAtIndex(output, start, 1);
      }
      return processed;
    }

    /**
     * Parse a number like 2.4 or 2.4e6
     */
    function parseNumber() {
      const start = i;
      if (text.charCodeAt(i) === codeMinus) {
        i++;
        if (expectDigitOrRepair(start)) {
          return true;
        }
      }
      if (text.charCodeAt(i) === codeZero) {
        i++;
      } else if (isNonZeroDigit(text.charCodeAt(i))) {
        i++;
        while (isDigit(text.charCodeAt(i))) {
          i++;
        }
      }
      if (text.charCodeAt(i) === codeDot) {
        i++;
        if (expectDigitOrRepair(start)) {
          return true;
        }
        while (isDigit(text.charCodeAt(i))) {
          i++;
        }
      }
      if (text.charCodeAt(i) === codeLowercaseE || text.charCodeAt(i) === codeUppercaseE) {
        i++;
        if (text.charCodeAt(i) === codeMinus || text.charCodeAt(i) === codePlus) {
          i++;
        }
        if (expectDigitOrRepair(start)) {
          return true;
        }
        while (isDigit(text.charCodeAt(i))) {
          i++;
        }
      }
      if (i > start) {
        output += text.slice(start, i);
        return true;
      }
      return false;
    }

    /**
     * Parse keywords true, false, null
     * Repair Python keywords True, False, None
     */
    function parseKeywords() {
      return parseKeyword('true', 'true') || parseKeyword('false', 'false') || parseKeyword('null', 'null') ||
      // repair Python keywords True, False, None
      parseKeyword('True', 'true') || parseKeyword('False', 'false') || parseKeyword('None', 'null');
    }
    function parseKeyword(name, value) {
      if (text.slice(i, i + name.length) === name) {
        output += value;
        i += name.length;
        return true;
      }
      return false;
    }

    /**
     * Repair and unquoted string by adding quotes around it
     * Repair a MongoDB function call like NumberLong("2")
     * Repair a JSONP function call like callback({...});
     */
    function parseUnquotedString() {
      // note that the symbol can end with whitespaces: we stop at the next delimiter
      const start = i;
      while (i < text.length && !isDelimiter(text[i])) {
        i++;
      }
      if (i > start) {
        if (text.charCodeAt(i) === codeOpenParenthesis) {
          // repair a MongoDB function call like NumberLong("2")
          // repair a JSONP function call like callback({...});
          i++;
          parseValue();
          if (text.charCodeAt(i) === codeCloseParenthesis) {
            // repair: skip close bracket of function call
            i++;
            if (text.charCodeAt(i) === codeSemicolon) {
              // repair: skip semicolon after JSONP call
              i++;
            }
          }
          return true;
        } else {
          // repair unquoted string

          // first, go back to prevent getting trailing whitespaces in the string
          while (isWhitespace(text.charCodeAt(i - 1)) && i > 0) {
            i--;
          }
          const symbol = text.slice(start, i);
          output += symbol === 'undefined' ? 'null' : JSON.stringify(symbol);
          return true;
        }
      }
    }
    function expectDigit(start) {
      if (!isDigit(text.charCodeAt(i))) {
        const numSoFar = text.slice(start, i);
        throw new JSONRepairError("Invalid number '".concat(numSoFar, "', expecting a digit ").concat(got()), 2);
      }
    }
    function expectDigitOrRepair(start) {
      if (i >= text.length) {
        // repair numbers cut off at the end
        // this will only be called when we end after a '.', '-', or 'e' and does not
        // change the number more than it needs to make it valid JSON
        output += text.slice(start, i) + '0';
        return true;
      } else {
        expectDigit(start);
        return false;
      }
    }
    function throwInvalidCharacter(char) {
      throw new JSONRepairError('Invalid character ' + JSON.stringify(char), i);
    }
    function throwUnexpectedCharacter() {
      throw new JSONRepairError('Unexpected character ' + JSON.stringify(text[i]), i);
    }
    function throwUnexpectedEnd() {
      throw new JSONRepairError('Unexpected end of json string', text.length);
    }
    function throwObjectKeyExpected() {
      throw new JSONRepairError('Object key expected', i);
    }
    function throwColonExpected() {
      throw new JSONRepairError('Colon expected', i);
    }
    function throwInvalidUnicodeCharacter(start) {
      let end = start + 2;
      while (/\w/.test(text[end])) {
        end++;
      }
      const chars = text.slice(start, end);
      throw new JSONRepairError("Invalid unicode character \"".concat(chars, "\""), i);
    }
    function got() {
      return text[i] ? "but got '".concat(text[i], "'") : 'but reached end of input';
    }
  }
  function atEndOfBlockComment(text, i) {
    return text[i] === '*' && text[i + 1] === '/';
  }

  exports.JSONRepairError = JSONRepairError;
  exports.jsonrepair = jsonrepair;

}));
//# sourceMappingURL=jsonrepair.js.map
