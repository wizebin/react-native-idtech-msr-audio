import { NativeModules } from 'react-native';
import { parseSwipeData } from './universal/parse';

export const {
  activate,
  deactivate,
  swipe,
} = NativeModules.IDTECH_MSR_audio;

export default { activate, deactivate, swipe, parseSwipeData };

export * from './universal/parse';
