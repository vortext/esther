"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.codeZero = exports.codeUppercaseF = exports.codeUppercaseE = exports.codeUppercaseA = exports.codeTab = exports.codeSpace = exports.codeSlash = exports.codeSemicolon = exports.codeReturn = exports.codeQuote = exports.codePlus = exports.codeOpeningBracket = exports.codeOpeningBrace = exports.codeOpenParenthesis = exports.codeOne = exports.codeNine = exports.codeNewline = exports.codeMinus = exports.codeLowercaseF = exports.codeLowercaseE = exports.codeLowercaseA = exports.codeFormFeed = exports.codeDoubleQuote = exports.codeDot = exports.codeComma = exports.codeColon = exports.codeClosingBracket = exports.codeClosingBrace = exports.codeCloseParenthesis = exports.codeBackspace = exports.codeBackslash = exports.codeAsterisk = void 0;
exports.endsWithCommaOrNewline = endsWithCommaOrNewline;
exports.insertBeforeLastWhitespace = insertBeforeLastWhitespace;
exports.isControlCharacter = isControlCharacter;
exports.isDelimiter = isDelimiter;
exports.isDigit = isDigit;
exports.isDoubleQuote = isDoubleQuote;
exports.isDoubleQuoteLike = isDoubleQuoteLike;
exports.isHex = isHex;
exports.isNonZeroDigit = isNonZeroDigit;
exports.isQuote = isQuote;
exports.isSingleQuoteLike = isSingleQuoteLike;
exports.isSpecialWhitespace = isSpecialWhitespace;
exports.isStartOfValue = isStartOfValue;
exports.isValidStringCharacter = isValidStringCharacter;
exports.isWhitespace = isWhitespace;
exports.removeAtIndex = removeAtIndex;
exports.stripLastOccurrence = stripLastOccurrence;
// TODO: sort the codes
const codeBackslash = 0x5c; // "\"
exports.codeBackslash = codeBackslash;
const codeSlash = 0x2f; // "/"
exports.codeSlash = codeSlash;
const codeAsterisk = 0x2a; // "*"
exports.codeAsterisk = codeAsterisk;
const codeOpeningBrace = 0x7b; // "{"
exports.codeOpeningBrace = codeOpeningBrace;
const codeClosingBrace = 0x7d; // "}"
exports.codeClosingBrace = codeClosingBrace;
const codeOpeningBracket = 0x5b; // "["
exports.codeOpeningBracket = codeOpeningBracket;
const codeClosingBracket = 0x5d; // "]"
exports.codeClosingBracket = codeClosingBracket;
const codeOpenParenthesis = 0x28; // "("
exports.codeOpenParenthesis = codeOpenParenthesis;
const codeCloseParenthesis = 0x29; // ")"
exports.codeCloseParenthesis = codeCloseParenthesis;
const codeSpace = 0x20; // " "
exports.codeSpace = codeSpace;
const codeNewline = 0xa; // "\n"
exports.codeNewline = codeNewline;
const codeTab = 0x9; // "\t"
exports.codeTab = codeTab;
const codeReturn = 0xd; // "\r"
exports.codeReturn = codeReturn;
const codeBackspace = 0x08; // "\b"
exports.codeBackspace = codeBackspace;
const codeFormFeed = 0x0c; // "\f"
exports.codeFormFeed = codeFormFeed;
const codeDoubleQuote = 0x0022; // "
exports.codeDoubleQuote = codeDoubleQuote;
const codePlus = 0x2b; // "+"
exports.codePlus = codePlus;
const codeMinus = 0x2d; // "-"
exports.codeMinus = codeMinus;
const codeQuote = 0x27; // "'"
exports.codeQuote = codeQuote;
const codeZero = 0x30;
exports.codeZero = codeZero;
const codeOne = 0x31;
exports.codeOne = codeOne;
const codeNine = 0x39;
exports.codeNine = codeNine;
const codeComma = 0x2c; // ","
exports.codeComma = codeComma;
const codeDot = 0x2e; // "." (dot, period)
exports.codeDot = codeDot;
const codeColon = 0x3a; // ":"
exports.codeColon = codeColon;
const codeSemicolon = 0x3b; // ";"
exports.codeSemicolon = codeSemicolon;
const codeUppercaseA = 0x41; // "A"
exports.codeUppercaseA = codeUppercaseA;
const codeLowercaseA = 0x61; // "a"
exports.codeLowercaseA = codeLowercaseA;
const codeUppercaseE = 0x45; // "E"
exports.codeUppercaseE = codeUppercaseE;
const codeLowercaseE = 0x65; // "e"
exports.codeLowercaseE = codeLowercaseE;
const codeUppercaseF = 0x46; // "F"
exports.codeUppercaseF = codeUppercaseF;
const codeLowercaseF = 0x66; // "f"
exports.codeLowercaseF = codeLowercaseF;
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
//# sourceMappingURL=stringUtils.js.map