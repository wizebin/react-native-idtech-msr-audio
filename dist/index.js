(function (global, factory) {
  typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports, require('react-native'), require('buffer')) :
  typeof define === 'function' && define.amd ? define(['exports', 'react-native', 'buffer'], factory) :
  (global = global || self, factory(global['react-native-idtech-msr-audio'] = {}, global.reactNative, global.buffer));
}(this, (function (exports, reactNative, buffer) { 'use strict';

  function isBitSet(_byte, bitIndex) {
    var mask = 1 << bitIndex;
    return (_byte & mask) === mask;
  }
  function isSwipeEncrypted(data) {
    if (data[0] === 2) return true;
    return false;
  } // Format of swipe: StartByte(0x02) Length(little endian, 2B) Payload() CheckXOR CheckSUM ETX(0x03)

  function verifyEncryptedSwipeData(data) {
    if (data.length < 6) return {
      valid: false,
      message: 'Swipe data too short'
    };
    if (data[0] !== 2) return {
      valid: false,
      message: 'Swipe data does not start with correct token'
    };
    if (data[data.length - 1] !== 3) return {
      valid: false,
      message: 'Swipe data does not end with correct token'
    };
    var payloadLength = (data[2] << 8) + data[1];
    if (payloadLength + 6 !== data.length) return {
      valid: false,
      message: "Payload length value ".concat(payloadLength + 6, " does not match actual payload length ").concat(data.length)
    };
    var checksum = 0;
    var checkxor = 0;

    for (var dex = 3; dex < data.length - 3; dex += 1) {
      checksum += data[dex];
      checkxor ^= data[dex];
    }

    if (data[data.length - 2] !== (checksum & 255)) return {
      valid: false,
      message: "Payload checksum value ".concat(numberToBinary(data[data.length - 2]), " does not match actual checksum value ").concat(numberToBinary(checksum & 255))
    };
    if (data[data.length - 3] !== (checkxor & 255)) return {
      valid: false,
      message: "Payload checkxor value ".concat(numberToBinary(data[data.length - 2]), " does not match actual checkxor value ").concat(numberToBinary(checkxor & 255))
    };
    return {
      valid: true
    };
  }
  function parseEncryptedSwipeData(data) {
    var results = {
      tracks: [null, null, null],
      encryptedTracks: [null, null, null],
      serial: null,
      ksn: null,
      aes: false,
      valid: false,
      encrypted: true
    };
    var usableLength = data.length - 3; // Get track sizes from packet, bytes 5, 6, and 7 are track 1, 2, and 3

    var trackStartPosition = 5;
    if (trackStartPosition + 3 > usableLength) throw new Error('Data is too small to include track positions'); // Maybe don't throw

    var trackLengths = [data[trackStartPosition], data[trackStartPosition + 1], data[trackStartPosition + 2]];
    var nextTrackStartPosition = 10;
    var totalTrackLength = 0;

    for (var dex = 0; dex < trackLengths.length; dex += 1) {
      var trackLength = trackLengths[dex]; // packet byte 8 is a bitwise field indicating the presence of tracks

      if (trackLength === 0 || !isBitSet(data[8], dex)) {
        continue;
      }

      if (nextTrackStartPosition + trackLength > usableLength) throw new Error("Track length field ".concat(dex, " indicated an incorrect index for its track ").concat(nextTrackStartPosition + trackLength, " which exceeds the maximum index of ").concat(usableLength));
      results.tracks[dex] = data.toString('hex', nextTrackStartPosition, nextTrackStartPosition + trackLength); // end position is exclusive, not including the trackLength byte

      nextTrackStartPosition += trackLength;
      totalTrackLength += trackLength;
    } // Encrypted block


    if (9 > usableLength) throw new Error('Cannot determine encryption type, usable length is too small to include the flag');
    var encryptionType = data[8] >> 4 & 3;
    if (encryptionType === 0) results.aes = false;else if (encryptionType === 1) results.aes = true;else throw new Error("Encryption type flag is invalid ".concat(encryptionType));
    var blockSize = results.aes ? 16 : 8;
    var encryptedLengths = [Math.ceil(trackLengths[0] / blockSize) * blockSize, Math.ceil(trackLengths[1] / blockSize) * blockSize, Math.ceil(trackLengths[2] / blockSize) * blockSize];
    var totalEncryptedLength = 0;
    var encryptedStartPosition = 10 + totalTrackLength; // Could also use nextTrackStartPosition

    nextTrackStartPosition = encryptedStartPosition; // redundant

    for (var _dex = 0; _dex < encryptedLengths.length; _dex += 1) {
      var encryptedLength = encryptedLengths[_dex];

      if (encryptedLength === 0 || !isBitSet(data[9], _dex)) {
        continue;
      }

      if (nextTrackStartPosition + encryptedLength > usableLength) throw new Error("Encrypted track length field ".concat(_dex, " indicated an incorrect index for its track ").concat(encryptedStartPosition + encryptedLength, " which exceeds the maximum index of ").concat(usableLength));
      results.encryptedTracks[_dex] = data.toString('hex', nextTrackStartPosition, nextTrackStartPosition + encryptedLength);
      nextTrackStartPosition += encryptedLength;
      totalEncryptedLength += encryptedLength;
    } // KSN


    if (isBitSet(data[9], 7) && (isBitSet(data[9], 0) || isBitSet(data[9], 1) || isBitSet(data[9], 2))) {
      var ksnStart = usableLength - 10;

      if (ksnStart < 10 + totalTrackLength + totalEncryptedLength) {
        throw new Error('KSN cannot be parsed, ksn start position cannot be within tracks or encrypted tracks');
      }

      results.ksn = data.toString('hex', ksnStart, ksnStart + 10);
    } // serial


    if (isBitSet(data[8], 7)) {
      var serialStart = usableLength - 10 - (results.ksn ? 10 : 0);

      if (serialStart < 10 + totalTrackLength + totalEncryptedLength) {
        throw new Error('Serial cannot be parsed, serial start position cannot be within tracks or encrypted tracks');
      }

      results.serial = data.toString('hex', serialStart, serialStart + 10);
    }

    var missingTracks = !results.tracks[0] && !results.tracks[1] && !results.tracks[2];
    var missingEncrypted = !results.encryptedTracks[0] && !results.encryptedTracks[1] && !results.encryptedTracks[2];

    if (missingTracks && missingEncrypted) {
      throw new Error('All card tracks are missing, either card is incompatible or the swipe was imperfect, please try again');
    }

    results.valid = true;
    return results;
  }
  function parseUnencryptedSwipeData(data) {
    var results = {
      tracks: [null, null, null],
      valid: false,
      iso: false,
      encrypted: false
    };
    var state = {
      outsideTrack: true,
      start: 0,
      index: 0
    };

    for (var dex = 0; dex < data.length; dex += 1) {
      var _byte2 = data[dex];

      if (state.outsideTrack) {
        if (_byte2 === 0x25 || _byte2 === 0x3B) {
          state.outsideTrack = false;
          results.iso = true;
          results.type = _byte2 === 0x25 ? 'ISO1' : 'ISO2';
        } else if (_byte2 === 0x7F) {
          state.outsideTrack = false;
          results.iso = false;
          results.type = 'JIS';
        } else if (_byte2 === 0x0D) {
          if (dex === data.length - 1) {
            results.valid = true;
          } else {
            throw new Error('Unexpected character after ending character 0x0D');
          }
        } else {
          throw new Error('Unexpected non-track character');
        }
      } else {
        if (results.iso && _byte2 !== 0x3F || !results.iso && _byte2 !== 0x7F) continue;else {
          results.tracks[state.index] = data.toString('hex', state.start, dex + 1);
          state.start = dex + 1;
          state.outsideTrack = true;
        }
      }
    }

    return results;
  }
  /**
   *
   * @param {Buffer|string} data If passing a string parameter pass the format parameter if the string is not in hex format
   */

  function parseSwipeData(data) {
    var format = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 'hex';
    if (!data) return {
      valid: false
    };
    var bufferData = buffer.Buffer.isBuffer(data) ? data : buffer.Buffer.from(data, format);
    if (!bufferData || bufferData.length < 1) throw new Error('Buffer data formatted incorrectly');

    if (isSwipeEncrypted(bufferData)) {
      var verified = verifyEncryptedSwipeData(bufferData);
      if (!verified.valid) throw new Error(verified.message);
      return parseEncryptedSwipeData(bufferData);
    } else {
      return parseUnencryptedSwipeData(bufferData);
    }
  }

  function numberToBinary(number) {
    var string = number.toString(2);
    var nextGroupDiff = string.length % 8;

    if (nextGroupDiff > 0) {
      return '0'.repeat(8 - nextGroupDiff) + string;
    }

    return string;
  }

  var READERS = {
    UNIMAG_1: 1,
    UNIMAG_PRO: 2,
    UNIMAG_2: 3,
    SHUTTLE: 4
  };

  var _NativeModules$IDTECH = reactNative.NativeModules.IDTECH_MSR_audio,
      activate = _NativeModules$IDTECH.activate,
      deactivate = _NativeModules$IDTECH.deactivate,
      swipe = _NativeModules$IDTECH.swipe;
  var index = {
    activate: activate,
    deactivate: deactivate,
    swipe: swipe,
    parseSwipeData: parseSwipeData,
    READERS: READERS
  };

  exports.READERS = READERS;
  exports.activate = activate;
  exports.deactivate = deactivate;
  exports.default = index;
  exports.isBitSet = isBitSet;
  exports.isSwipeEncrypted = isSwipeEncrypted;
  exports.parseEncryptedSwipeData = parseEncryptedSwipeData;
  exports.parseSwipeData = parseSwipeData;
  exports.parseUnencryptedSwipeData = parseUnencryptedSwipeData;
  exports.swipe = swipe;
  exports.verifyEncryptedSwipeData = verifyEncryptedSwipeData;

  Object.defineProperty(exports, '__esModule', { value: true });

})));
