import { NativeModules } from 'react-native';
import { parseSwipeData } from './universal/parse';
import READERS from './universal/readers';

export const {
  activate,
  deactivate,
  swipe,
} = NativeModules.IDTECH_MSR_audio;

export default { activate, deactivate, swipe, parseSwipeData, READERS };

export * from './universal/index';
