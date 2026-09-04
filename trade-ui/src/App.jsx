import { useState } from 'react'
import BrokerAccounts from './components/BrokerAccounts'
import MutualFunds from './components/MutualFunds'
import MutualFundTransactions from './components/MutualFundTransactions'
import MutualFundValues from './components/MutualFundValues'
import Analytics from './components/Analytics'

const tabs = [
  ['brokers', 'Broker Accounts'],
  ['funds', 'Mutual Funds'],
  ['transactions', 'Transactions'],
  ['values', 'Fund Values'],
  ['analytics', 'Analytics']
]

export default function App() {
  const [activeTab, setActiveTab] = useState('brokers')

  return (
    <div className="app-shell">

      <header className="topbar">
        <div>
          <div className="eyebrow">
            TRADE PLATFORM
          </div>

          <h1>
            Trade Management
          </h1>
        </div>
      </header>

      <nav className="tabs">
        {tabs.map(([key, label]) => (
          <button
            key={key}
            className={
              activeTab === key
                ? 'tab active'
                : 'tab'
            }
            onClick={() =>
              setActiveTab(key)
            }
          >
            {label}
          </button>
        ))}
      </nav>

      <main className="content">

        {activeTab === 'brokers' && (
          <BrokerAccounts />
        )}

        {activeTab === 'funds' && (
          <MutualFunds />
        )}

        {activeTab === 'transactions' && (
          <MutualFundTransactions />
        )}

        {activeTab === 'values' && (
          <MutualFundValues />
        )}

        {activeTab === 'analytics' && (
          <Analytics />
        )}

      </main>

    </div>
  )
}