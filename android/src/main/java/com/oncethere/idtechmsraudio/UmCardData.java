package com.oncethere.idtechmsraudio;

/**
 * Created by aamirshehzad on 6/28/15.
 */
public class UmCardData {
  public byte[] byteData; //Raw data which was used to initialize this object.

  //Meta
  public boolean isValid; //All available fields were successfully parsed from the raw data
  public boolean isEncrypted; //Whether the output is from an encrypting reader or a non-encrypting reader
  public boolean isAesEncrypted; //Only valid if isEncrypted==TRUE. If true, AES cipher is used, otherwise it's TDES

  //Parsed parts. Even if isValid==TRUE, some fields may be nil if the reader did not output them.
  // For example, reader may not read all tracks of a card.

  //  Tracks
  //    If non-encrypting reader is used, it is the full plain text card data
  //    If     encrypting reader is used, it is the partially masked plain text card data
  public byte[] track1;
  public byte[] track2;
  public byte[] track3;

  //  Encrypted Tracks. Only in encrypting reader output
  public byte[] track1_encrypted;
  public byte[] track2_encrypted;
  public byte[] track3_encrypted;

  //  Other. Only in encrypting reader output
  public byte[] serialNumber;
  public byte[] KSN;

  public UmCardData(byte[] cardData) {
    if (cardData != null) {
      this.byteData = cardData;
      final byte[] bytes = cardData;
      final Integer len = cardData.length;

      //check validity and parse
      if (len >= 1) {
        //is valid encrypted swipe?
        if (bytes[0] == 0x02) {
          this.isEncrypted = true;
          this.verifyAndParse_encrypted(cardData);
        }
        //is valid unencrypted swipe?
        else {
          this.isEncrypted = false;
          this.verifyAndParse_unencrypted(cardData);
        }
      }
    }
  }

  private static boolean isBitSet(byte abyte, int bitIndex) {
    byte b = 1;
    byte mask = (byte)(b << (byte)bitIndex);
    byte result = (byte)(abyte & mask);
    if (result > 0)
      return true;
    return false;
  }

  private void verifyAndParse_encrypted(byte[] cardData) {
    //things to fill out
    ///NSData tracks [3] = {null, null, null};
    /// NSData track_enc[3] = {null, null, null};

    byte[] track1 = null;
    byte[] track2 = null;
    byte[] track3 = null;

    byte[] track_enc1 = null;
    byte[] track_enc2 = null;
    byte[] track_enc3 = null;

    byte[] serialNumber = null;
    byte[] ksn = null;
    boolean isAES = false; //true if AES, false if TDES
    boolean isSwipeDataValid = false;

    //shorthand
    final byte[] bytes = cardData;
    final Integer len = cardData.length;

    //Verify
    if (len < 6)
      return;
    // STX ETX
    if (bytes[0] != 0x02 || bytes[len - 1] != 0x03)
      return;
    // Length
    int payloadLen = (bytes[2] << 8) + (bytes[1]);
    if (payloadLen + 6 != len)
      return;
    // CheckXor and CheckSum
    int cksum = 0, ckxor = 0;
    for (int i = 3; i < len - 3; i++) {
      ckxor ^= bytes[i];
      cksum += bytes[i];
    }
    if (bytes[len - 2] != cksum || bytes[len - 3] != ckxor)
      return;

    int idx = 0;

    //get track1, track2, track3 length
    int[] trackLens = new int[3];
    idx = 5;

    if (CHECK_INDEX(idx + 3, len) == false) {
      this.stopParse(isSwipeDataValid, isAES, track1, track2, track3, track_enc1, track_enc2, track_enc3, ksn, serialNumber);
      return;
    }
    for (int i = 0; i < 3; i++) {
      trackLens[i] = bytes[idx + i];
    }
    //get masked track
    int trackLensSum = 0;
    idx = 10;
    for (int i = 0; i < 3; i++) {
      //skip if len is 0 or presence flag not set
      if (trackLens[i] == 0 || !isBitSet(bytes[8], i))
        continue;

      if (CHECK_INDEX(idx + trackLens[i], len) == false) {
        this.stopParse(isSwipeDataValid, isAES, track1, track2, track3, track_enc1, track_enc2, track_enc3, ksn, serialNumber);
        return;
      }
      // tracks[i] = [cardData subdataWithRange: NSMakeRange(idx, trackLens[i])];
      switch (i) {
      case 0: {
        for (int j = idx, k = 0; j <= trackLens[i]; k++, j++) {
          track1[k] = cardData[j];
        }
        break;
      }
      case 1: {
        for (int j = idx, k = 0; j <= trackLens[i]; k++, j++) {
          track2[k] = cardData[j];
        }
        break;
      }
      case 2: {
        for (int j = idx, k = 0; j <= trackLens[i]; k++, j++) {
          track3[k] = cardData[j];
        }
        break;
      }
      }

      idx += trackLens[i];
      trackLensSum += trackLens[i];
    }

    //determine encryption type (TDES or AES)
    idx = 8;
    if (CHECK_INDEX(idx + 1, len) == false) {
      this.stopParse(isSwipeDataValid, isAES, track1, track2, track3, track_enc1, track_enc2, track_enc3, ksn, serialNumber);
      return;
    }
    byte encType = (byte)((bytes[idx] >> 4) & 0x03);
    if (encType == 0x00) {
      isAES = false;
    }
    //if the lowest significant bit of the high nibble is high, the encrypted track output is AES
    else if (encType == 0x01) {
      isAES = true;
    } else {
      this.stopParse(isSwipeDataValid, isAES, track1, track2, track3, track_enc1, track_enc2, track_enc3, ksn, serialNumber);
      return;
    }

    //TODO: none encrypting encrypted swipe

    //get encrypted section
    int[] trackLens_enc = new int[3];
    int encryptionBlockSize = (isAES ? 16 : 8);
    for (int i = 0; i < 3; i++) {

      trackLens_enc[i] = (int)Math.ceil(trackLens[i] / (double)encryptionBlockSize) * encryptionBlockSize;
    }
    int trackLensSum_enc = 0;
    idx = 10 + trackLensSum;
    for (int i = 0; i < 3; i++) {
      //skip if len is 0 or presence flag not set
      if (trackLens_enc[i] == 0 || !isBitSet(bytes[9], i))
        continue;

      if (CHECK_INDEX(idx + trackLens_enc[i], len) == false) {
        this.stopParse(isSwipeDataValid, isAES, track1, track2, track3, track_enc1, track_enc2, track_enc3, ksn, serialNumber);
        return;
      }
      switch (i) {
      case 0: {
        for (int j = idx, k = 0; j <= trackLens_enc[i]; k++, j++) {
          track_enc1[k] = cardData[j];
        }
        break;
      }
      case 1: {
        for (int j = idx, k = 0; j <= trackLens_enc[i]; k++, j++) {
          track_enc2[k] = cardData[j];
        }
        break;
      }
      case 2: {
        for (int j = idx, k = 0; j <= trackLens_enc[i]; k++, j++) {
          track_enc3[k] = cardData[j];
        }
        break;
      }
      }
      ///track_enc[i] = [cardData subdataWithRange: NSMakeRange(idx, trackLens_enc[i])];
      idx += trackLens_enc[i];
      trackLensSum_enc += trackLens_enc[i];
    }

    //get KSN /it looks as if you are checking for the high bit of the status byte (correct for KSN) and at least one of the individual track status bytes here.  Please consider not combining these checks as commented out below
    if ((isBitSet(bytes[9], 7)))
    //&& (isBitSet(bytes[9], 0)|| isBitSet(bytes[9], 1)|| isBitSet(bytes[9], 2)   )   )
    {
      idx = (int)(len - 3 - 10);
      //would [idx = (int)(len - 13);] work? If counting from ETX char, this logic follows to me
      if (idx < 10 + trackLensSum + trackLensSum_enc) {
        this.stopParse(isSwipeDataValid, isAES, track1, track2, track3, track_enc1, track_enc2, track_enc3, ksn, serialNumber);
        return;
      }

      for (int i = idx, j = 0; i <= 10; i++, j++) {
        ksn[j] = cardData[i];
      }

      ///[cardData subdataWithRange: NSMakeRange(idx, 10)];
    }

    //get serial number
    if (isBitSet(bytes[8], 7)) {
      idx = (int)len - 3 - 10 - (ksn != null ? 10 : 0);
      // as above, if counting from back (ETX) of data block, after confirming status byte value, length will be len - 23
      if (idx < 10 + trackLensSum + trackLensSum_enc) {
        this.stopParse(isSwipeDataValid, isAES, track1, track2, track3, track_enc1, track_enc2, track_enc3, ksn, serialNumber);
        return;
      }
      for (int i = idx, j = 0; i <= 10; i++, j++) {
        serialNumber[j] = cardData[i];
      }
      //// serialNumber = [cardData subdataWithRange: NSMakeRange(idx, 10)];
    }

    //all checks and parsing succeeded
    isSwipeDataValid = true;

    this.stopParse(isSwipeDataValid, isAES, track1, track2, track3, track_enc1, track_enc2, track_enc3, ksn, serialNumber);
  }

  private boolean CHECK_INDEX(int I, int len) {
    if ((I) > len - 3)
      return false; //goto stopParse;
    return true;
  }

  private void stopParse(boolean isSwipeDataValid, boolean isAES, byte[] track1, byte[] track2, byte[] track3, byte[] track_enc1, byte[] track_enc2, byte[] track_enc3, byte[] ksn, byte[] serialNumber) {
    this.isValid = isSwipeDataValid;
    this.isAesEncrypted = isAES;
    this.track1 = track1;
    this.track2 = track2;
    this.track3 = track3;
    this.track1_encrypted = track_enc1;
    this.track2_encrypted = track_enc2;
    this.track3_encrypted = track_enc3;
    this.serialNumber = serialNumber;
    this.KSN = ksn;
  }

  private void verifyAndParse_unencrypted(byte[] cardData) {
    //things to fill out
    /// NSData *tracks[3] = {nil, nil, nil};
    byte[] track1 = null;
    byte[] track2 = null;
    byte[] track3 = null;
    boolean isSwipeDataValid = false;

    //shorthand
    final byte[] bytes = cardData;
    final Integer len = cardData.length;

    //parse manually
    boolean ps_isOutsideTrack = true; //parser state: false if outside of track, true if inside
    boolean ps_isISO = false; //ISO or JIS track
    int ps_trackStart = 0; //starting point of this track
    int ps_tracksI = 0; //track index
    for (int i = 0; i < len; i++) {
      final Byte b = bytes[i];
      if (ps_isOutsideTrack) {
        if (b == 0x25 || b == 0x3B) //Please consider also referencing the LRC byte which in masked data is an * such as
        // byte b2 = bytes[i+1]
        //if (b == 0x3F)
        //if (b2 == 0x2A) {}
        {
          ps_isOutsideTrack = false;
          ps_isISO = true; //Please note that a start sentinel of a "%" is only an ID Tech start sentinel (reader added) and is not related to ISO.
              //In masked data, the format code, which begins the card data, is masked.  You may be able to get a good indication of card type from the first four digits of card data, but it would have limitations.
        } else if (b == 0x7F) {
          ps_isOutsideTrack = false;
          ps_isISO = false;
        } else if (b == 0x0D) //a carriage return is not expected for a reader such as the Shuttle//see manual for details and examples
        {
          //if it is last char, parsing's done
          // and the whole thing is valid.
          if (i == len - 1)
            isSwipeDataValid = true;
          //there's unexpected char after ending character \x0D
          else
            break;
        }
        //unexpected char when expecting a track
        else
          break;
      } else {
        //skip track content
        if (ps_isISO ? (b != 0x3F) : (b != 0x7F))
          continue;
        //track ended
        else {

          //save track
          /// tracks[ps_tracksI++] = [NSData dataWithBytes:bytes + ps_trackStart length: i+1-ps_trackStart];
          switch (ps_tracksI) {
          case 0: {
            for (int j = 0, k = ps_trackStart; j <= i + 1 - ps_trackStart; k++, j++) {
              track1[j] = bytes[k];
            }
            break;
          }
          case 1: {
            for (int j = 0, k = ps_trackStart; j <= i + 1 - ps_trackStart; k++, j++) {
              track2[j] = bytes[k];
            }
            break;
          }
          case 2: {
            for (int j = 0, k = ps_trackStart; j <= i + 1 - ps_trackStart; k++, j++) {
              track3[j] = bytes[k];
            }
            break;
          }
          }
          ps_tracksI++;
          ps_trackStart = i + 1;
          ps_isOutsideTrack = true;
        }
      }
    }

    //save result
    this.isValid = isSwipeDataValid;
    this.track1 = track1;
    this.track2 = track2;
    this.track3 = track3;
  }

} //class
