from multiprocessing import Process

class Strategy(Process):

    def __init__(self):
	Process.__init__(self)
	
    def run():
	pass


class StrategyGenerator:

    def __init__(self):
	pass	

    def generate(self,lstStrat):
	for strat in lstStrat:
	    if strat == "simple":
		yield SimpleStrategy()
	    else:
		yield None


class SimpleStrategy(Strategy):

    def __init__(self):
	Strategy.__init__(self)
	self.symbols = ["QQQ"]

    def run(self):
	print "Start running SimpleStrategy"
	print "Symbols: " + ",".join(self.symbols)
