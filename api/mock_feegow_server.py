from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import urllib.parse

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        qs = urllib.parse.parse_qs(parsed.query)

        if path == '/professional/list':
            ativo = qs.get('ativo', [''])[0]
            unidade_id = qs.get('unidade_id', [''])[0]
            content = []
            if unidade_id == '1' and ativo == '1':
                content = [
                    { 'id': 123, 'nome': 'Dr. Test' },
                    { 'id': 456, 'nome': 'Dr. Second' }
                ]

            body = { 'success': True, 'content': content }
            resp = json.dumps(body).encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(resp)))
            self.end_headers()
            self.wfile.write(resp)
        else:
            self.send_response(404)
            self.end_headers()

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', 8081), Handler)
    print('Mock Feegow server running on :8081')
    server.serve_forever()
