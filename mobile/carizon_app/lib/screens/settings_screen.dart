import 'package:flutter/material.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    super.key,
    required this.appVersion,
    required this.packageName,
    required this.onClearCache,
    required this.onClearCookies,
    required this.onOpenInquiry,
  });

  final String appVersion;
  final String packageName;
  final Future<String> Function() onClearCache;
  final Future<String> Function() onClearCookies;
  final Future<String> Function() onOpenInquiry;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _processing = false;

  Future<void> _runAction(Future<String> Function() action) async {
    if (_processing) {
      return;
    }

    setState(() {
      _processing = true;
    });

    try {
      final message = await action();
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    } finally {
      if (mounted) {
        setState(() {
          _processing = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('설정')),
      body: ListView(
        children: [
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: const Text('앱 버전'),
            subtitle: Text('${widget.appVersion}\n${widget.packageName}'),
          ),
          const Divider(height: 1),
          ListTile(
            enabled: !_processing,
            leading: const Icon(Icons.cleaning_services_outlined),
            title: const Text('웹 캐시 삭제'),
            subtitle: const Text('WebView 캐시/로컬 스토리지를 초기화합니다.'),
            onTap: () => _runAction(widget.onClearCache),
          ),
          ListTile(
            enabled: !_processing,
            leading: const Icon(Icons.cookie_outlined),
            title: const Text('쿠키 삭제'),
            subtitle: const Text('로그인 세션 등 쿠키를 삭제합니다.'),
            onTap: () => _runAction(widget.onClearCookies),
          ),
          ListTile(
            enabled: !_processing,
            leading: const Icon(Icons.support_agent_outlined),
            title: const Text('문의하기'),
            subtitle: const Text('브라우저에서 문의 페이지를 엽니다.'),
            onTap: () => _runAction(widget.onOpenInquiry),
          ),
          if (_processing) const LinearProgressIndicator(minHeight: 2),
        ],
      ),
    );
  }
}
