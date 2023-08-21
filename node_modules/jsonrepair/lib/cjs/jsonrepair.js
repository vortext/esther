"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.jsonrepair = jsonrepair;
var _JSONRepairError = require("./JSONRepairError.js");
var _stringUtils = require("./stringUtils.js");
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
  const processedComma = parseCharacter(_stringUtils.codeComma);
  if (processedComma) {
    parseWhitespaceAndSkipComments();
  }
  if ((0, _stringUtils.isStartOfValue)(text[i]) && (0, _stringUtils.endsWithCommaOrNewline)(output)) {
    // start of a new value after end of the root level object: looks like
    // newline delimited JSON -> turn into a root level array
    if (!processedComma) {
      // repair missing comma
      output = (0, _stringUtils.insertBeforeLastWhitespace)(output, ',');
    }
    parseNewlineDelimitedJSON();
  } else if (processedComma) {
    // repair: remove trailing comma
    output = (0, _stringUtils.stripLastOccurrence)(output, ',');
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
    while ((normal = (0, _stringUtils.isWhitespace)(text.charCodeAt(i))) || (0, _stringUtils.isSpecialWhitespace)(text.charCodeAt(i))) {
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
    if (text.charCodeAt(i) === _stringUtils.codeSlash && text.charCodeAt(i + 1) === _stringUtils.codeAsterisk) {
      // repair block comment by skipping it
      while (i < text.length && !atEndOfBlockComment(text, i)) {
        i++;
      }
      i += 2;
      return true;
    }

    // find a line comment '// ...'
    if (text.charCodeAt(i) === _stringUtils.codeSlash && text.charCodeAt(i + 1) === _stringUtils.codeSlash) {
      // repair line comment by skipping it
      while (i < text.length && text.charCodeAt(i) !== _stringUtils.codeNewline) {
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
    return skipCharacter(_stringUtils.codeBackslash);
  }

  /**
   * Parse an object like '{"key": "value"}'
   */
  function parseObject() {
    if (text.charCodeAt(i) === _stringUtils.codeOpeningBrace) {
      output += '{';
      i++;
      parseWhitespaceAndSkipComments();
      let initial = true;
      while (i < text.length && text.charCodeAt(i) !== _stringUtils.codeClosingBrace) {
        let processedComma;
        if (!initial) {
          processedComma = parseCharacter(_stringUtils.codeComma);
          if (!processedComma) {
            // repair missing comma
            output = (0, _stringUtils.insertBeforeLastWhitespace)(output, ',');
          }
          parseWhitespaceAndSkipComments();
        } else {
          processedComma = true;
          initial = false;
        }
        const processedKey = parseString() || parseUnquotedString();
        if (!processedKey) {
          if (text.charCodeAt(i) === _stringUtils.codeClosingBrace || text.charCodeAt(i) === _stringUtils.codeOpeningBrace || text.charCodeAt(i) === _stringUtils.codeClosingBracket || text.charCodeAt(i) === _stringUtils.codeOpeningBracket || text[i] === undefined) {
            // repair trailing comma
            output = (0, _stringUtils.stripLastOccurrence)(output, ',');
          } else {
            throwObjectKeyExpected();
          }
          break;
        }
        parseWhitespaceAndSkipComments();
        const processedColon = parseCharacter(_stringUtils.codeColon);
        if (!processedColon) {
          if ((0, _stringUtils.isStartOfValue)(text[i])) {
            // repair missing colon
            output = (0, _stringUtils.insertBeforeLastWhitespace)(output, ':');
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
      if (text.charCodeAt(i) === _stringUtils.codeClosingBrace) {
        output += '}';
        i++;
      } else {
        // repair missing end bracket
        output = (0, _stringUtils.insertBeforeLastWhitespace)(output, '}');
      }
      return true;
    }
    return false;
  }

  /**
   * Parse an array like '["item1", "item2", ...]'
   */
  function parseArray() {
    if (text.charCodeAt(i) === _stringUtils.codeOpeningBracket) {
      output += '[';
      i++;
      parseWhitespaceAndSkipComments();
      let initial = true;
      while (i < text.length && text.charCodeAt(i) !== _stringUtils.codeClosingBracket) {
        if (!initial) {
          const processedComma = parseCharacter(_stringUtils.codeComma);
          if (!processedComma) {
            // repair missing comma
            output = (0, _stringUtils.insertBeforeLastWhitespace)(output, ',');
          }
        } else {
          initial = false;
        }
        const processedValue = parseValue();
        if (!processedValue) {
          // repair trailing comma
          output = (0, _stringUtils.stripLastOccurrence)(output, ',');
          break;
        }
      }
      if (text.charCodeAt(i) === _stringUtils.codeClosingBracket) {
        output += ']';
        i++;
      } else {
        // repair missing closing array bracket
        output = (0, _stringUtils.insertBeforeLastWhitespace)(output, ']');
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
        const processedComma = parseCharacter(_stringUtils.codeComma);
        if (!processedComma) {
          // repair: add missing comma
          output = (0, _stringUtils.insertBeforeLastWhitespace)(output, ',');
        }
      } else {
        initial = false;
      }
      processedValue = parseValue();
    }
    if (!processedValue) {
      // repair: remove trailing comma
      output = (0, _stringUtils.stripLastOccurrence)(output, ',');
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
    let skipEscapeChars = text.charCodeAt(i) === _stringUtils.codeBackslash;
    if (skipEscapeChars) {
      // repair: remove the first escape character
      i++;
      skipEscapeChars = true;
    }
    if ((0, _stringUtils.isQuote)(text.charCodeAt(i))) {
      const isEndQuote = (0, _stringUtils.isSingleQuoteLike)(text.charCodeAt(i)) ? _stringUtils.isSingleQuoteLike : (0, _stringUtils.isDoubleQuote)(text.charCodeAt(i)) ? _stringUtils.isDoubleQuote // eslint-disable-line indent
      : _stringUtils.isDoubleQuoteLike; // eslint-disable-line indent

      output += '"';
      i++;
      while (i < text.length && !isEndQuote(text.charCodeAt(i))) {
        if (text.charCodeAt(i) === _stringUtils.codeBackslash) {
          const char = text[i + 1];
          const escapeChar = escapeCharacters[char];
          if (escapeChar !== undefined) {
            output += text.slice(i, i + 2);
            i += 2;
          } else if (char === 'u') {
            if ((0, _stringUtils.isHex)(text.charCodeAt(i + 2)) && (0, _stringUtils.isHex)(text.charCodeAt(i + 3)) && (0, _stringUtils.isHex)(text.charCodeAt(i + 4)) && (0, _stringUtils.isHex)(text.charCodeAt(i + 5))) {
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
          if (code === _stringUtils.codeDoubleQuote && text.charCodeAt(i - 1) !== _stringUtils.codeBackslash) {
            // repair unescaped double quote
            output += '\\' + char;
            i++;
          } else if ((0, _stringUtils.isControlCharacter)(code)) {
            // unescaped control character
            output += controlCharacters[char];
            i++;
          } else {
            if (!(0, _stringUtils.isValidStringCharacter)(code)) {
              throwInvalidCharacter(char);
            }
            output += char;
            i++;
          }
        }
        if (skipEscapeChars) {
          const processed = skipEscapeCharacter();
          if (processed) {
            // repair: skipped escape character (nothing to do)
          }
        }
      }
      if ((0, _stringUtils.isQuote)(text.charCodeAt(i))) {
        if (text.charCodeAt(i) !== _stringUtils.codeDoubleQuote) {
          // repair non-normalized quote
        }
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
    while (text.charCodeAt(i) === _stringUtils.codePlus) {
      processed = true;
      i++;
      parseWhitespaceAndSkipComments();

      // repair: remove the end quote of the first string
      output = (0, _stringUtils.stripLastOccurrence)(output, '"', true);
      const start = output.length;
      parseString();

      // repair: remove the start quote of the second string
      output = (0, _stringUtils.removeAtIndex)(output, start, 1);
    }
    return processed;
  }

  /**
   * Parse a number like 2.4 or 2.4e6
   */
  function parseNumber() {
    const start = i;
    if (text.charCodeAt(i) === _stringUtils.codeMinus) {
      i++;
      if (expectDigitOrRepair(start)) {
        return true;
      }
    }
    if (text.charCodeAt(i) === _stringUtils.codeZero) {
      i++;
    } else if ((0, _stringUtils.isNonZeroDigit)(text.charCodeAt(i))) {
      i++;
      while ((0, _stringUtils.isDigit)(text.charCodeAt(i))) {
        i++;
      }
    }
    if (text.charCodeAt(i) === _stringUtils.codeDot) {
      i++;
      if (expectDigitOrRepair(start)) {
        return true;
      }
      while ((0, _stringUtils.isDigit)(text.charCodeAt(i))) {
        i++;
      }
    }
    if (text.charCodeAt(i) === _stringUtils.codeLowercaseE || text.charCodeAt(i) === _stringUtils.codeUppercaseE) {
      i++;
      if (text.charCodeAt(i) === _stringUtils.codeMinus || text.charCodeAt(i) === _stringUtils.codePlus) {
        i++;
      }
      if (expectDigitOrRepair(start)) {
        return true;
      }
      while ((0, _stringUtils.isDigit)(text.charCodeAt(i))) {
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
    while (i < text.length && !(0, _stringUtils.isDelimiter)(text[i])) {
      i++;
    }
    if (i > start) {
      if (text.charCodeAt(i) === _stringUtils.codeOpenParenthesis) {
        // repair a MongoDB function call like NumberLong("2")
        // repair a JSONP function call like callback({...});
        i++;
        parseValue();
        if (text.charCodeAt(i) === _stringUtils.codeCloseParenthesis) {
          // repair: skip close bracket of function call
          i++;
          if (text.charCodeAt(i) === _stringUtils.codeSemicolon) {
            // repair: skip semicolon after JSONP call
            i++;
          }
        }
        return true;
      } else {
        // repair unquoted string

        // first, go back to prevent getting trailing whitespaces in the string
        while ((0, _stringUtils.isWhitespace)(text.charCodeAt(i - 1)) && i > 0) {
          i--;
        }
        const symbol = text.slice(start, i);
        output += symbol === 'undefined' ? 'null' : JSON.stringify(symbol);
        return true;
      }
    }
  }
  function expectDigit(start) {
    if (!(0, _stringUtils.isDigit)(text.charCodeAt(i))) {
      const numSoFar = text.slice(start, i);
      throw new _JSONRepairError.JSONRepairError("Invalid number '".concat(numSoFar, "', expecting a digit ").concat(got()), 2);
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
    throw new _JSONRepairError.JSONRepairError('Invalid character ' + JSON.stringify(char), i);
  }
  function throwUnexpectedCharacter() {
    throw new _JSONRepairError.JSONRepairError('Unexpected character ' + JSON.stringify(text[i]), i);
  }
  function throwUnexpectedEnd() {
    throw new _JSONRepairError.JSONRepairError('Unexpected end of json string', text.length);
  }
  function throwObjectKeyExpected() {
    throw new _JSONRepairError.JSONRepairError('Object key expected', i);
  }
  function throwColonExpected() {
    throw new _JSONRepairError.JSONRepairError('Colon expected', i);
  }
  function throwInvalidUnicodeCharacter(start) {
    let end = start + 2;
    while (/\w/.test(text[end])) {
      end++;
    }
    const chars = text.slice(start, end);
    throw new _JSONRepairError.JSONRepairError("Invalid unicode character \"".concat(chars, "\""), i);
  }
  function got() {
    return text[i] ? "but got '".concat(text[i], "'") : 'but reached end of input';
  }
}
function atEndOfBlockComment(text, i) {
  return text[i] === '*' && text[i + 1] === '/';
}
//# sourceMappingURL=jsonrepair.js.map