import React, { useEffect, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { NativeModules, View, Text } from 'react-native';
import LoginScreen from './src/screens/LoginScreen';

const { TokenModule } = NativeModules;

export default function App() {
  const [token, setToken] = useState(null);
  const [booting, setBooting] = useState(true);

  useEffect(() => {
    (async () => {
      const saved = await AsyncStorage.getItem('token');
      if (saved) {
        setToken(saved);
        if (TokenModule?.setToken) TokenModule.setToken(saved);
      }
      setBooting(false);
    })();
  }, []);

  if (booting) {
    return (
      <View style={{ padding: 20 }}>
        <Text>Cargando...</Text>
      </View>
    );
  }

  if (!token) {
    return <LoginScreen onLogin={setToken} />;
  }

  return (
    <View style={{ padding: 20 }}>
      <Text>Sesión activa</Text>
      <Text>Token guardado y listo para enviar notificaciones.</Text>
    </View>
  );
}
