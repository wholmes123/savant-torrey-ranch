from datetime import date
from random import randint
import sqlite3 as sqlite

from sqlalchemy import (Boolean, Column, Date, Enum, ForeignKey, Index,
                        Integer, Float, String, Text, TypeDecorator, event,
                        Sequence, Table, DateTime)
from sqlalchemy.engine import Engine
from sqlalchemy.orm import relationship, backref
from sqlalchemy import UniqueConstraint
from savant import db


@event.listens_for(Engine, 'connect')
def set_sqlite_pragma(dbapi_connection, connection_record):
    """
    See http://docs.sqlalchemy.org/en/latest/dialects/sqlite.html#foreign-key-support
    """
    if isinstance(dbapi_connection, sqlite.Connection):
        cursor = dbapi_connection.cursor()
        cursor.execute('PRAGMA foreign_keys=ON')
        cursor.close()

CBase = db.Base 
########################Schema ########################
class Company(CBase):
    __tablename__ = "company"

    id = Column(Integer, primary_key=True)
    name = Column(String(100), nullable=False)

    # Company ticker symbol
    symbol = Column(String(10), nullable=False, unique=True)

    # Exchange
    exchange_id = Column(Integer, ForeignKey("exchange.id"))

    # Sector
    sector_id = Column(Integer, ForeignKey("sector.id"))

    # Industry
    industry_id = Column(Integer, ForeignKey("industry.id"))

    # Company primary location
    #headquarter = Column(String(100))

    # Market cap
    market_cap = Column(Integer)

    # Public shares as of the date this table is updated
    float_shares = Column(Integer)

    # Closing price as of the date this table is updated
    prev_close = Column(Float)

    # Volume as of the date this table is updated
    prev_volume = Column(Integer)

    # P/E ratio as of the date this table is updated
    trailing_pe = Column(Float)

    # Beta rating for NASDAQ tickers
    nasdaq_beta = Column(Float)

    # Date of the last update
    date_updated = Column(Date)

    # Relationship
    underwriters = relationship("CompanyUnderwriterAssociation")

    def __init__(self, **params):
        #params["date_updated"] = date.today()
        self.__dict__.update(params)

    def clone(self, params):
        self.__dict__.update(params)

class Exchange(CBase):
    __tablename__ = "exchange"

    id = Column(Integer, primary_key=True)
    name = Column(String(10), nullable=False, unique=True)

    company = relationship("Company", backref="exchange")

    def __init__(self, name):
        #self.company_id = company_id
        self.name = name

    def clone(self, params):
        self.__dict__.update(params)

    def __repr__(self):
        return "<Exchange(name='%s')>" % (self.name)


class Sector(CBase):
    __tablename__ = "sector"

    id = Column(Integer, primary_key=True)
    name = Column(String(20), nullable=False, unique=True)

    company = relationship("Company", backref="sector")

    def __init__(self, name):
        self.name = name

    def clone(self, params):
        self.__dict__.update(params)

    def __repr__(self):
        return "<Sector(name='%s')>" % self.name


class Industry(CBase):
    __tablename__ = "industry"

    id = Column(Integer, primary_key=True)
    name = Column(String(20), nullable=False, unique=True)

    company = relationship("Company", backref="industry")

    def __init__(self, name):
        self.name = name

    def clone(self, params):
        self.__dict__.update(params)

    def __repr__(self):
        return "<Industry(name='%s')>" % self.name


class CompanyUnderwriterAssociation(CBase):
    __tablename__ = "company_underwriter_association"

    company_id = Column(Integer, ForeignKey("company.id"), primary_key=True)
    underwriter_id = Column(Integer, ForeignKey("underwriter.id"), primary_key=True)
    lead = Column(Boolean)
    company = relationship("Underwriter", backref="companies")

    def __init__(self, company_id, underwriter_id, lead):
        self.company_id = company_id
        self.underwriter_id = underwriter_id
        self.lead = lead

    def clone(self, params):
        self.__dict__.update(params)


class Underwriter(CBase):
    __tablename__ = "underwriter"

    id = Column(Integer, primary_key=True)
    name = Column(String(100), nullable=False, unique=True)

    def __init__(self, name):
        self.name = name

    def clone(self, params):
        self.__dict__.update(params)

    def __repr__(self):
        return "<Underwriter(name='%s')>" % self.name


class IPOInfoUrl(CBase):
    __tablename__ = "ipo_url"

    id = Column(Integer, primary_key=True)
    name = Column(String(50), nullable=False, unique=True)
    symbol = Column(String(10), nullable=False, unique=True)
    url = Column(String(100), unique=True)

    def __init__(self, name, symbol, url):
        self.name = name
        self.symbol = symbol
        self.url = url

    def clone(self, params):
        self.__dict__.update(params)

    def __repr__(self):
        return "<IPOInfoURL(company_name='%s', symbol='%s', url='%s')>" % (self.name, self.symbol, self.url)


class HistoricalIPO(CBase):
    __tablename__ = "historical_ipo"

    company_id = Column(Integer, ForeignKey("company.id"), primary_key=True)

    ipo_date = Column(Date)
    price = Column(Float)
    shares = Column(Integer)
    outstanding = Column(Integer)
    scoop_rating = Column(Integer)

    # Related to first day trading
    open_vol = Column(Integer)
    first_opening_price = Column(Float)
    first_closing_price = Column(Float)
    first_trade_time = Column(String)
    first_day_high = Column(Float)
    first_day_high_percent_change = Column(Float)
    first_day_low = Column(Float)
    first_day_low_percent_change = Column(Float)
    first_day_volume = Column(Integer)

    # Related to finance
    revenue = Column(Integer)
    net_income = Column(Integer)
    total_assets = Column(Integer)
    total_liability = Column(Integer)
    stakeholder_equity = Column(Integer)
    validity = Column(Integer)

    company = relationship("Company", foreign_keys='HistoricalIPO.company_id')

    def __init__(self, **params):
        self.__dict__.update(params)

    def __repr__(self):
        return "<Historical_IPO(company_id='%s', ipo_date='%s', price='%s', shares='%s', outstanding='%s', scoop_rating='%s')>" % (self.company_id, self.ipo_date, self.price, self.shares, self.outstanding, self.scoop_rating)

class PostIPOPriceAT(CBase):
    __tablename__ = "post_ipo_price_at"

    id = Column(Integer, primary_key=True)
    company_id = Column(Integer, ForeignKey("company.id"), primary_key=False)

    date= Column(Date)
    open = Column(Float)
    high = Column(Float)
    low = Column(Float)
    close = Column(Float)
    volume = Column(Integer)

    company = relationship("Company", foreign_keys='PostIPOPriceAT.company_id')
    __table_args__ = (UniqueConstraint('company_id', 'date', name='uix_1'), )


    def __init__(self, **params):
        self.__dict__.update(params)

    def __repr__(self):
        return "<Post_IPO_Price_AT(company_id='%s', datetime='%s', open='%s', high='%s', low='%s', close='%s', volume='%s')>" % (self.company_id, self.date, self.open, self.high, self.low, self.close, self.volume)

class PostIPOPriceYahoo(CBase):
    __tablename__ = "post_ipo_price_yh"

    id = Column(Integer, primary_key=True)
    company_id = Column(Integer, ForeignKey("company.id"), primary_key=False)

    date= Column(Date)
    open = Column(Float)
    high = Column(Float)
    low = Column(Float)
    close = Column(Float)
    volume = Column(Integer)

    company = relationship("Company", foreign_keys='PostIPOPriceYahoo.company_id')
    __table_args__ = (UniqueConstraint('company_id', 'date', name='uix_1'), )


    def __init__(self, **params):
        self.__dict__.update(params)

    def __repr__(self):
        return "<Post_IPO_Price_YH(company_id='%s', datetime='%s', open='%s', high='%s', low='%s', close='%s', volume='%s')>" % (self.company_id, self.date, self.open, self.high, self.low, self.close, self.volume)

#class IPOVolume(CBase):
#    __tablename__ = "ipo_volume" 
#  
#    id = Column(Integer, primary_key=True)
#    company_id = Column(Integer, ForeignKey("company.id"), primary_key=False)
#
#    first_trade_vol = Column(Integer)
#    first_second_vol = Column(Integer)
#    first_minute_vol = Column(Integer)
#    first_5m_vol = Column(Integer)
#    first_30m_vol = Column(Integer)
#    first_1h_vol = Column(Integer)
#    first_1d_markethour_vol = Column(Integer)
#    first_1d_aftermarket_vol = Column(Integer)
#    
#    company = relationship("Company", foreign_keys='IPOVolume.company_id')
#
#    def __init__(self, params):
#        self.__dict__.update(params)
#
#    def __repr__(self):
#        return "<IPO_Volume(symbol='%s', first_trade_vol='%s', first_second_vol='%s', first_minute_vol='%s', first_5m_vol='%s', first_30m_vol='%s', first_1h_vol='%s', first_1d_markethour_vol='%s', first_1d_aftermarket_vol='%s')>" % (self.company_id, self.first_trade_vol, self.first_second_vol, self.first_minute_vol, self.first_5m_vol, self.first_30m_vol, self.first_1h_vol, self.first_1d_markethour_vol, self.first_1d_aftermarket_vol)

class MarketIndex(CBase):
    __tablename__ = "market_index"

    id = Column(Integer, primary_key=True)
    name = Column(String(100), nullable=False)
    symbol = Column(String(10), nullable=False, unique=True)

    def __init__(self, **params):
        self.__dict__.update(params)

    def clone(self, params):
        self.__dict__.update(params)

class Daily(CBase):
    __tablename__ = "daily"

    id = Column(Integer, primary_key=True)
    symbol = Column(String(10))
    date = Column(Date)
    open = Column(Float)
    high = Column(Float)
    low = Column(Float)
    close = Column(Float)
    volume = Column(Integer)
    tick_downloaded = Column(Boolean, default=False)
    minbar_downloaded = Column(Boolean, default=False)

    __table_args__ = (UniqueConstraint('symbol', 'date', name='uix_1'), )



#########################End Schema ##########################
