import React, { useEffect, useState } from 'react';
import { SafeAreaView, Button, Alert, Text } from 'react-native';
import nodejs from 'nodejs-mobile-react-native';

const App: React.FC = () => {
  const [serverUrl, setServerUrl] = useState<string | null>(null);

  useEffect(() => {
    nodejs.start('main.js');

    const listener = (msg: any) => {
      console.log('[react-native] from node:', msg);

      if (typeof msg === 'object' && msg.type === 'started') {
        setServerUrl(`http://${msg.ip}:${msg.port}`);
      } else {
        Alert.alert('From Node', JSON.stringify(msg));
      }
    };

    nodejs.channel.addListener('message', listener);

    return () => {
      nodejs.channel.removeListener('message', listener);
    };
  }, []);

  const pingNode = () => {
    nodejs.channel.send('ping from React Native');
  };

  return (
    <SafeAreaView style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
      <Text>Node.js Server</Text>
      {serverUrl ? (
        <Text selectable={true} style={{ marginVertical: 12 }}>{serverUrl}</Text>
      ) : (
        <Text>Starting server...</Text>
      )}
      <Button title="Ping Node.js" onPress={pingNode} />
    </SafeAreaView>
  );
};

export default App;
