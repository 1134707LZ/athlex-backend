import json, os, urllib.request, urllib.error, sys
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "")
B = "http://localhost:8080"
res = []
def call(method, path, body=None, token=None, headers=None):
    h = {"Content-Type": "application/json"}
    if token: h["Authorization"] = "Bearer " + token
    if headers: h.update(headers)
    req = urllib.request.Request(B + path, data=json.dumps(body).encode() if body is not None else None, headers=h, method=method)
    try:
        r = urllib.request.urlopen(req, timeout=30); raw = r.read(); code = r.status
    except urllib.error.HTTPError as e:
        raw = e.read(); code = e.code
    try: data = json.loads(raw) if raw else None
    except Exception: data = raw.decode(errors="ignore")[:200]
    return code, data
def chk(n, desc, got, exp, extra=""):
    ok = got == exp
    res.append(ok); print(f"{'OK  ' if ok else 'FAIL'} {n:>2} {desc}: {got} (esperado {exp}) {extra}")
    return ok

prod = {"nome":"Bola Teste","descricao":"Bola de teste e2e","preco":50,"categoria":"Bolas","tamanhos":["Único"],"cores":["#FFFFFF"],"personalizavel":False,"estoque":5}
end = {"cep":"90000000","rua":"Rua A","numero":"10","cidade":"POA","uf":"RS"}

c,_ = call("POST","/auth/signup",{"name":"Loja","email":"v@x.com","phone":"51999990000","password":"123456","profile":"VENDEDOR"}); chk(1,"signup vendedor",c,201)
c,_ = call("POST","/auth/signup",{"name":"Cliente","email":"c@x.com","phone":"51988880000","password":"123456","profile":"COMPRADOR"}); chk(2,"signup comprador",c,201)
c,_ = call("POST","/auth/signup",{"name":"Cliente","email":"c@x.com","phone":"51988880000","password":"123456"}); chk(3,"signup duplicado",c,409)
c,d = call("POST","/auth/signin",{"email":"v@x.com","password":"123456"}); chk(4,"signin vendedor",c,200,f"type={d['user']['type'] if c==200 else ''}"); tv = d["token"] if c==200 else None
c,d = call("POST","/auth/signin",{"email":"c@x.com","password":"123456"}); chk(4,"signin comprador",c,200,f"type={d['user']['type'] if c==200 else ''}"); tc = d["token"] if c==200 else None
c,d = call("POST","/auth/signin",{"email":"admin@admin.dev","password":ADMIN_PASSWORD}); chk(4,"signin admin",c,200); ta = d["token"] if c==200 else None
c,_ = call("POST","/auth/signin",{"email":"c@x.com","password":"errada"}); chk(5,"senha errada",c,401)
c,d = call("GET","/products"); chk(6,"listar sem token",c,200,f"n={len(d) if isinstance(d,list) else '?'} img={'sim' if isinstance(d,list) and d and d[0].get('imagem') else 'não'}")
cat = d if isinstance(d,list) else []
c,d = call("GET","/products?categoria=Bolas"); chk(7,"filtro categoria",c,200,f"n={len(d) if isinstance(d,list) else '?'}")
c,d = call("GET","/products?busca=camisa"); chk(7,"busca texto",c,200,f"n={len(d) if isinstance(d,list) else '?'}")
c,_ = call("POST","/ws/products",prod); chk(8,"POST produto sem token",c,401)
c,d = call("POST","/ws/products",prod,tc); chk(9,"POST produto comprador",c,403,str(d))
c,d = call("POST","/ws/products",prod,tv); chk(10,"POST produto vendedor",c,201); pid = d["id"] if c==201 else None
c,d = call("PUT",f"/ws/products/{pid}",{**prod,"preco":60},tv); chk(11,"PUT próprio produto",c,200,f"preco={d.get('preco') if isinstance(d,dict) else ''}")
c,d = call("DELETE","/ws/products/1",None,tv); chk(12,"DELETE produto de outro (loja demo)",c,403,str(d))
c,d = call("POST","/ws/orders",{"endereco":end,"pagamento":"PIX","itens":[{"produtoId":int(pid),"quantidade":99,"tamanho":"Único","cor":"#FFFFFF"}]},tc); chk(13,"pedido sem estoque",c,400,str(d))
c,d = call("POST","/ws/orders",{"endereco":end,"pagamento":"PIX","itens":[{"produtoId":int(pid),"quantidade":2,"tamanho":"Único","cor":"#FFFFFF"}]},tc); chk(14,"pedido ok",c,201,f"total={d.get('total') if isinstance(d,dict) else d} (esperado 120.0 = 2x60)")
c,d = call("GET",f"/products/{pid}"); chk(15,"estoque baixou",d.get("estoque") if isinstance(d,dict) else None,3)
camisa = next((p for p in cat if p["personalizavel"]), None)
pers = {"modelo":"Polo","cor":"#2563EB","nome":"SILVA","numero":"10","acrescimo":20}
c,d = call("POST","/ws/orders",{"endereco":end,"pagamento":"CARTAO","itens":[{"produtoId":int(camisa["id"]),"quantidade":1,"tamanho":"M","cor":"#2563EB","personalizacao":pers}]},tc)
chk(16,"pedido com personalização",c,201,f"total={d.get('total') if isinstance(d,dict) else d} (esperado {camisa['preco']+20:.2f})")
nao = next((p for p in cat if not p["personalizavel"]), None)
c,d = call("POST","/ws/orders",{"endereco":end,"pagamento":"PIX","itens":[{"produtoId":int(nao["id"]),"quantidade":1,"tamanho":"Único","cor":"#FFFFFF","personalizacao":pers}]},tc); chk(17,"personalizar produto não personalizável",c,400,str(d))
c,d = call("GET","/ws/orders",None,tc); chk(18,"histórico do comprador",c,200,f"n={len(d) if isinstance(d,list) else '?'} (esperado 2)")
c,d = call("GET","/ws/orders/sales",None,tv); chk(19,"vendas do vendedor",c,200,f"n={len(d) if isinstance(d,list) else '?'}")
c,d = call("GET","/ws/orders/sales/summary",None,tv); chk(19,"resumo vendedor",c,200,str(d))
c,d = call("GET","/ws/orders/sales",None,tc); chk(20,"vendas p/ comprador",c,403,str(d))
c,d = call("POST","/ws/products",prod,tc,{"X-User-Type":"2"}); chk(21,"header X-User-Type forjado",c,403)
c,_ = call("POST","/internal/products/1/reserve",{"quantity":1}); chk(22,"/internal exposto?",c,404)
c,d = call("GET","/ws/auth/users",None,tc); chk(23,"listar usuários comprador",c,403)
c,d = call("GET","/ws/auth/users",None,ta); chk(23,"listar usuários admin",c,200,f"n={len(d) if isinstance(d,list) else '?'}")
c,d = call("GET","/ws/orders/sales",None,ta); chk(24,"vendas admin",c,200)
print(f"\n{sum(res)}/{len(res)} OK"); sys.exit(0 if all(res) else 1)
