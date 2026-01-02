import React, { useState } from 'react';
import { View, Text, TextInput, Button, Alert } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { NativeModules } from 'react-native';

const { TokenModule } = NativeModules;

const API_BASE = 'https://paynotify-api.yumi.net.pe';

export default function LoginScreen({ onLogin }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async () => {
    if (!email || !password) {
      Alert.alert('Validación', 'Ingresa email y contraseña.');
      return;
    }

    try {
      setLoading(true);

      const res = await fetch(`${API_BASE}/api/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({
          email,
          password,
          device_name: 'Android',
        }),
      });

      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        const msg =
          data?.error || data?.message || 'No se pudo iniciar sesión.';
        Alert.alert('Login', msg);
        return;
      }

      if (data?.token) {
        await AsyncStorage.setItem('token', data.token);

        if (TokenModule?.setToken) {
          TokenModule.setToken(data.token);
        }

        onLogin(data.token);
      } else {
        Alert.alert('Login', 'Respuesta sin token. Revisa el backend.');
      }
    } catch (e) {
      Alert.alert(
        'Error',
        'No se pudo conectar al servidor. Revisa tu internet o la URL.',
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={{ padding: 20 }}>
      <Text>Email</Text>
      <TextInput
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
        style={{ borderWidth: 1, marginBottom: 10, paddingHorizontal: 10 }}
      />

      <Text>Password</Text>
      <TextInput
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        style={{ borderWidth: 1, marginBottom: 10, paddingHorizontal: 10 }}
      />

      <Button
        title={loading ? 'Ingresando...' : 'Iniciar sesión'}
        onPress={handleLogin}
        disabled={loading}
      />
    </View>
  );
}
