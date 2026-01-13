import React, { useMemo, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Alert,
  StyleSheet,
  Pressable,
  ActivityIndicator,
  StatusBar,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { NativeModules } from 'react-native';

const { TokenModule } = NativeModules;
const API_BASE = 'https://paynotify-api.yumi.net.pe';

export default function LoginScreen({ onLogin }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);

  const canSubmit = useMemo(() => {
    return email.trim().length > 0 && password.trim().length > 0 && !loading;
  }, [email, password, loading]);

  const handleLogin = async () => {
    const e = email.trim();
    const p = password.trim();

    if (!e || !p) {
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
          email: e,
          password: p,
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

      if (!data?.token) {
        Alert.alert('Login', 'Respuesta sin token. Revisa el backend.');
        return;
      }

      await AsyncStorage.setItem('token', data.token);
      TokenModule?.setToken?.(data.token);
      onLogin?.(data.token);
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
    <View style={styles.container}>
      <StatusBar barStyle="dark-content" />

      <View style={styles.header}>
        <Text style={styles.brand}>PayNotify</Text>
        <Text style={styles.subtitle}>
          Inicia sesión para habilitar el envío de notificaciones
        </Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.label}>Email</Text>
        <TextInput
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          keyboardType="email-address"
          placeholder="correo@dominio.com"
          placeholderTextColor="#94a3b8"
          style={styles.input}
          editable={!loading}
          returnKeyType="next"
        />

        <Text style={[styles.label, { marginTop: 12 }]}>Contraseña</Text>
        <View style={styles.passRow}>
          <TextInput
            value={password}
            onChangeText={setPassword}
            secureTextEntry={!showPass}
            placeholder="••••••••"
            placeholderTextColor="#94a3b8"
            style={[styles.input, { flex: 1, marginBottom: 0 }]}
            editable={!loading}
            returnKeyType="done"
            onSubmitEditing={handleLogin}
          />
          <Pressable
            style={styles.showBtn}
            onPress={() => setShowPass(v => !v)}
            disabled={loading}
          >
            <Text style={styles.showBtnText}>
              {showPass ? 'Ocultar' : 'Ver'}
            </Text>
          </Pressable>
        </View>

        <Pressable
          style={[styles.btn, !canSubmit && styles.btnDisabled]}
          onPress={handleLogin}
          disabled={!canSubmit}
        >
          {loading ? (
            <View style={styles.btnRow}>
              <ActivityIndicator />
              <Text style={styles.btnText}>Ingresando...</Text>
            </View>
          ) : (
            <Text style={styles.btnText}>Iniciar sesión</Text>
          )}
        </Pressable>

        <Text style={styles.note}>
          Nota: el acceso a notificaciones se configura en Ajustes del sistema.
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
    paddingHorizontal: 18,
    paddingTop: 30,
  },
  header: {
    marginBottom: 14,
  },
  brand: {
    fontSize: 28,
    fontWeight: '800',
    color: '#0f172a',
  },
  subtitle: {
    marginTop: 6,
    fontSize: 14,
    color: '#475569',
    lineHeight: 18,
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  label: {
    fontSize: 12,
    fontWeight: '700',
    color: '#334155',
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    backgroundColor: '#ffffff',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 12,
    fontSize: 14,
    color: '#0f172a',
    marginBottom: 4,
  },
  passRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  showBtn: {
    paddingHorizontal: 12,
    paddingVertical: 12,
    borderRadius: 12,
    backgroundColor: '#f1f5f9',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  showBtnText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#0f172a',
  },
  btn: {
    marginTop: 14,
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
    backgroundColor: '#0f172a',
  },
  btnDisabled: {
    opacity: 0.6,
  },
  btnText: {
    color: '#ffffff',
    fontWeight: '800',
    fontSize: 14,
  },
  btnRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  note: {
    marginTop: 10,
    fontSize: 12,
    color: '#64748b',
    lineHeight: 16,
  },
  footer: {
    marginTop: 14,
    textAlign: 'center',
    fontSize: 12,
    color: '#94a3b8',
  },
});
