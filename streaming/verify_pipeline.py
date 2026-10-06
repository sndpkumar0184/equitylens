"""Bounded read-only REST verification after replay and Spark processing."""
import argparse
import json
from urllib.request import urlopen
from urllib.error import HTTPError


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--backend',default='http://127.0.0.1:8080')
    args=parser.parse_args()
    for symbol in ['AAPL','META','AMZN','NVDA']:
        base=args.backend.rstrip('/')+'/api/market/realtime/'+symbol
        with urlopen(base,timeout=15) as response: state=json.load(response)
        assert state['symbol']==symbol and state['provider']=='SYNTHETIC' and state['synthetic'] is True
        assert state['bar'] and state['analytics'], f'{symbol}: no persisted aggregates; wait for Spark watermark advancement'
        assert state['bar']['volume']>0 and state['bar']['vwap'] is not None
        for route in ['bars?interval=1m','bars?interval=5m','analytics','anomalies']:
            with urlopen(base+'/'+route,timeout=15) as response: rows=json.load(response)
            assert isinstance(rows,list) and len(rows)<=100
        try:
            urlopen(base+'/bars?limit=501',timeout=15)
            raise AssertionError('Unbounded limit accepted')
        except HTTPError as error: assert error.code==400
        print(f'{symbol}: persisted bars/analytics/source metadata and bounded REST PASS')
    print('Synthetic pipeline verification complete; this does not verify entitled live provider access.')


if __name__=='__main__': main()
